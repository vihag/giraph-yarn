package com.noob.odlinks;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.giraph.GiraphRunner;
import org.apache.giraph.conf.StrConfOption;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.graph.BasicComputation;
import org.apache.giraph.graph.Vertex;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.util.ToolRunner;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
com.noob.odlinks.ODRoutesV4 -vip src/main/resources/odlinks_vertexformat/ -vif com.noob.odlinks.TextTextDoubleAdjacencyListVertexInputFormat -vof org.apache.giraph.io.formats.AdjacencyListTextVertexOutputFormat -op od_routes -w 1 -ca giraph.SplitMasterWorker=false,giraph.logLevel=error -ca sourceId=758963 -ca giraph.useBigDataIOForMessages=true
 */

/*
yarn jar odlinks-1.0-SNAPSHOT-jar-with-dependencies.jar org.apache.giraph.GiraphRunner com.noob.odlinks.ODRoutesV4  -vif com.noob.odlinks.TextTextDoubleAdjacencyListVertexInputFormat -vip /user/vihaggupta/od_input -vof org.apache.giraph.io.formats.IdWithValueTextOutputFormat -op /user/vihaggupta/output -w 2 -ca giraph.SplitMasterWorker=true -yj odlinks-1.0-SNAPSHOT-jar-with-dependencies.jar
 */

/*
giraph -Dmapred.job.tracker=ip-10-0-1-24.ap-southeast-2.compute.internal:8032 odlinks-1.0-SNAPSHOT-jar-with-dependencies.jar  com.noob.odlinks.ODRoutesV4  -vif com.noob.odlinks.TextTextDoubleAdjacencyListVertexInputFormat -vip /user/vihaggupta/odlinks_input/ -vof org.apache.giraph.io.formats.IdWithValueTextOutputFormat -op /user/vihaggupta/odlinks_output -w 150 -ca giraph.SplitMasterWorker=true -yj odlinks-1.0-SNAPSHOT-jar-with-dependencies.jar -ca giraph.logLevel=debug -ca sourceId=758963 -ca redisIp=10.0.1.238 -ca redisPort=6379 -ca giraph.useBigDataIOForMessages=true -ca giraph.yarn.libjars=/home/vihaggupta/giraph/odlinks-1.0-SNAPSHOT-jar-with-dependencies.jar -ca giraph.zkList=10.0.1.47:2181
 */

/*
In version 5
1. If the user specified no source Id, the job will try to generate all paths from all nodes
2. Save the value of the vertex as another ID to reduce Disk I/O
3. The ID will be a key to a redis set which contains all the paths that the vertex knows
4. the paths are stored as sorted set (with score = index) instead of vanilla sets(vanilla sets wont guarentee order preservation hence ..)


1000 paths per second with 25 workers (+1 master)
 */

@Slf4j
public class ODRoutesV5 extends BasicComputation<Text, Text, DoubleWritable, Text>
{

  //private static final String BANNER_STRING = ">>>>>>>";
  private static final String VERTEX_PREFIX = "vertex_";

  public static final StrConfOption SOURCE_ID =
      new StrConfOption("sourceId", "-1",
                        "Source from which all paths will be calculated"
      );

  public static final StrConfOption REDIS_IP =
      new StrConfOption("redisIp", "localhost",
                        "Redis IP address"
      );

  public static final StrConfOption REDIS_PORT =
      new StrConfOption("redisPort", "6379",
                        "Redis Port"
      );

  private Jedis jedisConnection;

  private Jedis getJedisConnection()
  {

    if (null == jedisConnection || !jedisConnection.isConnected()) {
      jedisConnection = new Jedis(REDIS_IP.get(getConf()), Integer.parseInt(REDIS_PORT.get(getConf())), 120000);
    }


    return jedisConnection;
  }

  private void deleteJedisConnection()
  {
    if (null != jedisConnection) {
      jedisConnection.close();
    }
  }

  public static void main(String[] args)
  {
    try {
      System.exit(ToolRunner.run(new GiraphRunner(), args));
    }
    catch (Exception e) {
      log.error(">>This went wrong : " + e);
      e.printStackTrace();
    }
  }

  /**
   * Must be defined by user to do computation on a single Vertex.
   *
   * @param vertex   Vertex
   * @param messages Messages that were sent to this vertex in the previous
   *                 superstep.  Each message is only guaranteed to have
   */
  @Override
  public void compute(
      Vertex<Text, Text, DoubleWritable> vertex, Iterable<Text> messages
  ) throws IOException
  {

    if (getSuperstep() == 0) {

      //The vertice's value will be its own id, which will double up as a key in Redis which will contain the actual set of paths that this vertex knows (either from a source, or from all sources)
      //A constant vertex value will reduce the per superstep overhead of updating the vertex values(which should hopefully stop unnecessary repartitions of data)
      //This should also reduce the need to extra disk persistence at the end of supersteps (as the amount to write would grow exponenetially if we stored the paths as vertex values
      vertex.setValue(new Text(VERTEX_PREFIX.concat(vertex.getId().toString())));

      if (isVertexSource(vertex)) {

        log.info(vertex.getId() + " is source vertex");

        StringBuilder path = new StringBuilder();
        path.append(vertex.getId());

        //Get Path ID as MD5 Digest
        String pathId = vertex.getId() + generateUniquePathId(path);

        //Push path to Redis via Jedis (Push to a sorted set with the source as Rank 0)
        getJedisConnection().zadd(pathId, 0, path.toString());

        //Send Path ID to all connected edges(with directionality in mind)
        for (Edge<Text, DoubleWritable> e : vertex.getEdges()) {
          // log.debug(BANNER_STRING + vertex.getId() + " sending path ID " + pathId + " to " + e.getTargetVertexId());
          sendMessage(e.getTargetVertexId(), new Text(pathId));
        }

      }


    } else {

      List<String> newPathsDiscovered = new ArrayList<>();

      //Process each received path id
      for (Text pathId : messages) {

        //log.debug(vertex.getId() + " is processing path ID " + pathId.toString());
        //Check with redis if the set in redis with key 'pathId' contains the element 'vertex.getId'
        //sismember method can check the presence of member in logarithmic time complexity, worst case O(1)
        boolean loopPossiblity = null != getJedisConnection().zrank(pathId.toString(), vertex.getId().toString());

        if (loopPossiblity) {

          //reject the path
          continue;

        } else {

          //Get the vertices in the path from the Redis sorted set (ordering starts at 0 - source and onwards)
          //From https://redis.io/commands/zrangebyscore
          //Returns all the elements in the sorted set at key with a score between min and max (including elements with score equal to min or max). The elements are considered to be ordered from low to high scores.
          final Set<String> verticesInPath = getJedisConnection().zrange(
              pathId.toString(),
              Long.MIN_VALUE,
              Long.MAX_VALUE
          );

          //Generate a path id with for the newly minted path
          verticesInPath.add(vertex.getId().toString());
          String newPathId = generateUniquePathId(verticesInPath);

          //Add the new path definiton into Redis via Jedis
          double index = 0;
          Map<String, Double> vertexMap = new HashMap<>();
          for (String vertexInPath : verticesInPath) {
            vertexMap.put(vertexInPath, index++);
          }

          if (vertexMap.size() > 0) {
            getJedisConnection().zadd(newPathId, vertexMap);
          }

          //Add the new path to a roster of newly discovered paths, to be sent to neighbors after processing all received paths
          newPathsDiscovered.add(newPathId);

        }
      }
      //Add each new path to the the redis key (vertex_<vertexid>) in a simple set (this avoids the need to manually merge the exiting paths that the vertex knows with the new paths discoverd. this was previously a string operation which required spliting the csv string representing the value of the vertex and comparing each split token with the new paths discovered)
      //Since the the newPaths are represented by md5 hashes of the vertices that consitute the path, the comparision becomes ridiculously inexpensive
      double index = 0;
      Map<String, Double> pathMap = new HashMap<>();
      for (String newPath : newPathsDiscovered) {
        //constant ranking
        pathMap.put(newPath, index++);
      }

      if (pathMap.size() > 0) {
        getJedisConnection().zadd(vertex.getValue().toString(), pathMap);
      }

      //newPathsDiscovered.forEach(newPath -> jedisConnection.sadd(vertex.getValue().toString(), newPath));

      //Send the newly discovered paths to all connected vertices
      for (Edge<Text, DoubleWritable> e : vertex.getEdges()) {
        //log.debug(vertex.getId() + " sending paths " + String.join(",", newPathsDiscovered) + " to " + e.getTargetVertexId());
        //send a single message per new path discovered instead of combining them into a delimited string
        //i am assuming that this String manipulation will be over time become more costly than the message passing latency
        if (isVertexSource(e.getTargetVertexId())) {
          //dont send messages to the source vertex to avoid endless paths
          continue;
        }
        newPathsDiscovered.forEach(newPath -> sendMessage(e.getTargetVertexId(), new Text(newPath)));
      }                                     

    }

    this.deleteJedisConnection();
    //log.debug(BANNER_STRING + "Vertex : " + vertex.getId() + " is voting to halt in Superstep " + getSuperstep());
    vertex.voteToHalt();

  }

  private boolean isVertexSource(Text targetVertexId)
  {
    return SOURCE_ID.get(getConf()).toString().equals(targetVertexId);
  }

  public boolean isVertexSource(Vertex<Text, Text, DoubleWritable> vertex)
  {
    if (SOURCE_ID.get(getConf()).toString().equals("-1")) {
      //If the user ignores the sourceId command line option, take it as a signal to calculate all paths from all vertices
      return true;
    }
    return vertex.getId().toString().equals(SOURCE_ID.get(getConf()).toString());
  }

  private String generateUniquePathId(Set<String> path)
  {
    StringBuilder pathBuilder = new StringBuilder();
    path.forEach(vertex -> pathBuilder.append(vertex));
    return generateUniquePathId(pathBuilder);
  }

  private String generateUniquePathId(StringBuilder path)
  {
    return DigestUtils.md5Hex(path.toString());
  }


}
