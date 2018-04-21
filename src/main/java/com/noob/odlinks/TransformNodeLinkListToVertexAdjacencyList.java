package com.noob.odlinks;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransformNodeLinkListToVertexAdjacencyList
{

  public void transform()
  {


    File vertexFile = new File("src/main/resources/odlinks/sample-link-list_20180308.txt");
    BufferedReader bufferedReader;

    File vertextOutputFile = new File("src/main/resources/odlinks_vertexformat/sample-link-list_20180308.txt");
    BufferedWriter bufferedWriter;

    Map<String, List<String>> vertexMap = new HashMap<>();

    try {

      new File("src/main/resources/odlinks_vertexformat").mkdirs();

      bufferedReader = new BufferedReader(new FileReader(vertexFile));
      bufferedWriter = new BufferedWriter(new FileWriter(vertextOutputFile));
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        final String[] split = line.split(",");
        if (vertexMap.containsKey(split[0])) {
          vertexMap.get(split[0]).add(split[1]);
        } else {
          List<String> _list = new ArrayList<>();
          _list.add(split[1]);
          vertexMap.put(split[0], _list);
        }
      }
      bufferedReader.close();
      for (Map.Entry<String, List<String>> listEntry : vertexMap.entrySet()) {
        bufferedWriter.write(listEntry.getKey().concat("\t").concat("0"));
        for (String s : listEntry.getValue()) {
          bufferedWriter.write("\t".concat(s).concat("\t").concat("0"));
        }
        bufferedWriter.newLine();
      }

      bufferedWriter.flush();
      bufferedWriter.close();
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }


  }

  public static void main(String[] args)
  {
    new TransformNodeLinkListToVertexAdjacencyList().transform();
  }
}
