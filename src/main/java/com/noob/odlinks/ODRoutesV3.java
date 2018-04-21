package com.noob.odlinks;

import lombok.extern.slf4j.Slf4j;
import org.apache.giraph.GiraphRunner;
import org.apache.giraph.conf.StrConfOption;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.graph.BasicComputation;
import org.apache.giraph.graph.Vertex;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
com.noob.odlinks.ODRoutesV3 -vip src/main/resources/odlinks_vertexformat/ -vif com.noob.odlinks.TextTextDoubleAdjacencyListVertexInputFormat -vof org.apache.giraph.io.formats.AdjacencyListTextVertexOutputFormat -op od_routes -w 1 -ca giraph.SplitMasterWorker=false,giraph.logLevel=error -ca sourceId=758963 -ca giraph.useBigDataIOForMessages=true
 */

/*
yarn jar odlinks-1.0-SNAPSHOT-jar-with-dependencies.jar org.apache.giraph.GiraphRunner com.noob.odlinks.ODRoutesV3  -vif com.noob.odlinks.TextTextDoubleAdjacencyListVertexInputFormat -vip /user/vihaggupta/od_input -vof org.apache.giraph.io.formats.IdWithValueTextOutputFormat -op /user/vihaggupta/output -w 2 -ca giraph.SplitMasterWorker=true -yj odlinks-1.0-SNAPSHOT-jar-with-dependencies.jar
 */

/*
giraph -Dmapred.job.tracker=ip-10-0-1-24.ap-southeast-2.compute.internal:8032 odlinks-1.0-SNAPSHOT-jar-with-dependencies.jar  com.noob.odlinks.ODRoutesV3  -vif com.noob.odlinks.TextTextDoubleAdjacencyListVertexInputFormat -vip /user/vihaggupta/odlinks_input -vof org.apache.giraph.io.formats.IdWithValueTextOutputFormat -op /user/vihaggupta/odlinks_output -w 100 -ca giraph.SplitMasterWorker=true -yj odlinks-1.0-SNAPSHOT-jar-with-dependencies.jar -ca giraph.logLevel=debug -ca sourceId=758963 -ca giraph.useBigDataIOForMessages=true -ca giraph.yarn.libjars=/home/vihaggupta/giraph/odlinks-1.0-SNAPSHOT-jar-with-dependencies.jar -ca giraph.zkList=10.0.1.47:2181
 */

@Slf4j
public class ODRoutesV3 extends BasicComputation<Text, Text, DoubleWritable, Text>
{

  public static final StrConfOption SOURCE_ID =
      new StrConfOption("sourceId", "1254622",
                        "Source from which all paths will be calculated"
      );

  public boolean isVertexSource(Vertex<Text, Text, DoubleWritable> vertex)
  {
    return vertex.getId().toString().equals(SOURCE_ID.get(getConf()).toString());
  }

  //param 1 : vertex value
  //param 2 : new calculated vertex value
  private Text mergePaths(
      Text value,
      StringBuilder allPaths
  )
  {
    log.info("Merging " + value + " with " + allPaths);
    //Get a string representation of all existing paths
    List<String> stringPaths = new ArrayList<>();
    for (String s : value.toString().split(",", -1)) {
      stringPaths.add(s);
    }

    for (String s : allPaths.toString().split(",", -1)) {
      if (!stringPaths.contains(s)) {
        value = new Text(value.toString().concat(",").concat(s));
      }
    }


    log.info("Merged paths : " + value);
    return value;
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

        for (Edge<Text, DoubleWritable> e : vertex.getEdges()) {
          log.info(vertex.getId() + " sending path " + path + " to " + e.getTargetVertexId());
          sendMessage(e.getTargetVertexId(), new Text(path.toString()));
        }

      }

    } else {

      StringBuilder allPaths = new StringBuilder();

      for (Text pathSet : messages) {
        for (String path : pathSet.toString().split(",", -1)) {
          boolean isPathRejected = false;
          log.info(vertex.getId() + " is processing path : " + path);
          for (String node : path.split("-")) {
            if (node.toString().equals(vertex.getId().toString())) {
              //Reject this path
              isPathRejected = true;
              break;
            }
          }
          if (isPathRejected) {
            continue;
          }
          path.concat("-").concat(vertex.getId().toString());
          allPaths.append(path);

          log.info(vertex.getId()
                   + " has value "
                   + vertex.getValue()
                   + " add adding new paths : "
                   + allPaths);

          vertex.setValue(mergePaths(vertex.getValue(), allPaths));

          for (Edge<Text, DoubleWritable> e : vertex.getEdges()) {
            log.info(vertex.getId() + " sending paths " + allPaths + " to " + e.getTargetVertexId());
            sendMessage(e.getTargetVertexId(), new Text(allPaths.toString()));
          }

        }

      }

      vertex.voteToHalt();

    }
  }
}
