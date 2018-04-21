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
import java.util.List;
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
In version 4
1. store each path as a path id instead of a full blown delimited path
2. path id will be the MD digest of the path
3. storage will be redis, as a redis set (set name is MD5 digest, value is list of vertices constituting the paths
4. check for loop possibility : lookup redis for received path to check the existence of current vertex in the set, if present loop possible else not
5. at each superstep only the path ids will be propagated as a comma delimited string
 */

@Slf4j
public class ODRoutesV4 extends BasicComputation<Text, Text, DoubleWritable, Text>
{

  private static final String BANNER_STRING = ">>>>>>>>>>>>>>";

  public static final StrConfOption SOURCE_ID =
      new StrConfOption("sourceId", "1254622",
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

    if (null == jedisConnection) {
      jedisConnection = new Jedis(REDIS_IP.get(getConf()), Integer.parseInt(REDIS_PORT.get(getConf())));
    }
    return jedisConnection;

  }

  private void deleteJedisConnection()
  {
    if (null != jedisConnection) {
      jedisConnection.close();
    }
  }

  public boolean isVertexSource(Vertex<Text, Text, DoubleWritable> vertex)
  {
    return vertex.getId().toString().equals(SOURCE_ID.get(getConf()).toString());
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

      if (isVertexSource(vertex)) {

        log.info(vertex.getId() + " is source vertex");

        StringBuilder path = new StringBuilder();
        path.append(vertex.getId());

        //Get Path ID as MD5 Digest
        String pathId = vertex.getId() + generateUniquePathId(path);

        //Push path to Redis via Jedis
        getJedisConnection().sadd(pathId, path.toString());

        //Send Path ID to all connected edges(with directionality in mind)
        for (Edge<Text, DoubleWritable> e : vertex.getEdges()) {
          log.debug(BANNER_STRING + vertex.getId() + " sending path ID " + pathId + " to " + e.getTargetVertexId());
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
        boolean loopPossiblity = getJedisConnection().sismember(pathId.toString(), vertex.getId().toString());

        if (loopPossiblity) {
          //reject the path
          continue;
        } else {
          //Get the vertices in the path

          final Set<String> verticesInPath = getJedisConnection().smembers(pathId.toString());
          //Generate a path id with for the newly minted path
          verticesInPath.add(vertex.getId().toString());
          String newPathId = generateUniquePathId(verticesInPath);
          //Add the new path definiton into Redis via Jedis
          verticesInPath.forEach(vertexInPath -> getJedisConnection().sadd(newPathId, vertexInPath));
          //Add the new path to a roster of newly discovered paths, to be sent to neighbors after processing all received paths
          newPathsDiscovered.add(newPathId);

        }
      }

      vertex.setValue(mergePaths(vertex.getValue(), newPathsDiscovered));
      System.err.println(BANNER_STRING.concat("Sending " + newPathsDiscovered.size() + " messages from vertex " + vertex
          .getId()));
      //Send the newly discovered paths to all connected vertices
      for (Edge<Text, DoubleWritable> e : vertex.getEdges()) {
        //log.debug(vertex.getId() + " sending paths " + String.join(",", newPathsDiscovered) + " to " + e.getTargetVertexId());
        //send a single message per new path discovered instead of combining them into a delimited string
        //i am assuming that this String manipulation will be over time become more costly than the message passing latency
        newPathsDiscovered.forEach(newPath -> sendMessage(e.getTargetVertexId(), new Text(newPath)));
      }

    }
    this.deleteJedisConnection();
    //log.debug(BANNER_STRING + "Vertex : " + vertex.getId() + " is voting to halt in Superstep " + getSuperstep());
    vertex.voteToHalt();

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

  //param 1 : vertex value
  //param 2 : new paths discovered
  private Text mergePaths(
      Text value,
      List<String> allPaths
  )
  {

    //log.info("Merging " + value.toString() + " with " + String.join(",", allPaths));

    for (String vertex : value.toString().split(",")) {
      if (!allPaths.contains(vertex)) {
        allPaths.add(vertex);
      }
    }

    //log.info("Merged paths : " + String.join(",", allPaths));
    return new Text(String.join(",", allPaths));
  }
}
