package com.noob.odlinkswithjava;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ODLinks
{

  private static List<Vertex> listOfVertices = new ArrayList<>();

  private static Vertex getVertex(String vertexId)
  {
    for (Vertex vertex : listOfVertices) {
      if (vertex.getId().equalsIgnoreCase(vertexId)) {
        return vertex;
      }
    }

    Vertex vertex = new Vertex(vertexId);

    listOfVertices.add(vertex);

    return vertex;
  }

  private static List<String> readGraphLines(String directory, String file) throws IOException
  {
    List<String> graphLines = new ArrayList<>();

    BufferedReader bufferedReader = new BufferedReader(new FileReader(new File(directory, file)));
    String line;
    while ((line = bufferedReader.readLine()) != null) {
      graphLines.add(line);
    }

    return graphLines;
  }

  private static void initalizeVertices(String directory, String file)
  {

    try {


      final List<String> graphLines = readGraphLines(directory, file);

      graphLines.forEach(e -> {

        final String[] split = e.split(",");

        Vertex v1 = getVertex(split[0]);
        Vertex v2 = getVertex(split[1]);
        Vertex v3 = getVertex(split[2]);

        v1.addVertexAsNeighbor(v2);
        v2.addVertexAsNeighbor(v3);


      });

    }
    catch (FileNotFoundException e) {
      System.err.println("File not found " + e);
    }
    catch (IOException e) {
      System.err.println("IO Exception in reading file " + e);
    }
  }

  private static void printVertices()
  {

    listOfVertices.forEach(e -> {
      final StringBuilder vertexRepresentation = new StringBuilder(e.getId());
      vertexRepresentation.append(" : ");
      e.getNeighbors().forEach(f -> vertexRepresentation.append(f.getId().concat(",")));
      System.out.println(vertexRepresentation.substring(0, vertexRepresentation.length() - 1));
    });

    System.out.println("Loaded " + listOfVertices.size() + " vertices");

  }

  //0 -> init/send
//1 -> process
//2 -> send
//3 -> process
//4 -> send
  private static void findAllRoutesFromVertex(Vertex sourceVertex, String directory)
  {

    long stepCounter = 0L;

    List<Vertex> verticesWithNewPaths = new ArrayList<>();

    do {

      if (stepCounter == 0) {

        //Source vertex initiates the message passing

        Path sourcePath = new Path();
        sourcePath.addVertexToPath(sourceVertex);

        sourceVertex.getNeighbors().forEach(neighbor -> neighbor.addMessage(sourcePath));
        for (Vertex neighbor : sourceVertex.getNeighbors()) {
          verticesWithNewPaths.add(neighbor);
        }

      } else if (stepCounter % 2 == 0) {

        //even steps indicate message send
        if(verticesWithNewPaths.size() == 0)  break;

        verticesWithNewPaths.forEach(vertexWithNewPaths -> vertexWithNewPaths.getNeighbors()
                                                                             .forEach(neighbor -> neighbor.addMessage(
                                                                                 vertexWithNewPaths.getNewFoundPathsToSource())));

        verticesWithNewPaths.forEach(vertex -> vertex.resetNewFoundPathsToSource());

        verticesWithNewPaths = new ArrayList<>();


      } else if (stepCounter % 2 != 0) {

        //odd steps indicate message processing

        for (Vertex vertex : listOfVertices) {

          //skip the source vertex
          if (vertex.getId() == sourceVertex.getId()) {
            continue;
          }
          if (vertex.processMessages()) {
            verticesWithNewPaths.add(vertex);
          }

        }


      }

      System.err.println("Step counter : " + stepCounter);

      //Print the values of each vertex at this step
      if(stepCounter % 10 == 0){
        try {
          File stateFile = new File(directory, "state_superstep_".concat(String.valueOf(stepCounter)));
          if(stateFile.exists())  stateFile.delete();
          PrintWriter stateWriter = new PrintWriter(stateFile);
          listOfVertices.forEach(vertex -> stateWriter.println(vertex.toString()));
          stateWriter.flush();
          stateWriter.close();
        }
        catch (FileNotFoundException e) {
          e.printStackTrace();
        }
      }


      stepCounter++;

    } while (true);

  }

  public static void main(String[] args)
  {

    String directory = args[0];
    String file = args[1];

    initalizeVertices(directory, file);
    printVertices();
    findAllRoutesFromVertex(getVertex("758963"), directory);
    try {
      File stateFile = new File(directory, "allRoutesFromSource_758963");
      if(stateFile.exists())  stateFile.delete();
      PrintWriter stateWriter = new PrintWriter(stateFile);
      listOfVertices.forEach(vertex -> stateWriter.println(vertex.toString()));
      stateWriter.flush();
      stateWriter.close();
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

}
