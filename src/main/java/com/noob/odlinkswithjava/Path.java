package com.noob.odlinkswithjava;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class Path
{

  private static long pathCounter;

  @Getter
  private final long id;

  @Getter
  final private Map<String, Vertex> vertices;


  public Path()
  {
    this.id = pathCounter++;
    this.vertices = new HashMap<>();
  }

  public Path(Path path)
  {
    this.id = pathCounter++;
    this.vertices = new HashMap<>();
    this.vertices.putAll(path.getVertices());
  }


  @Override
  public boolean equals(Object o)
  {

    Path otherObject = ((Path) o);

    for (Map.Entry<String, Vertex> vertex : this.vertices.entrySet()) {

      if (otherObject.getVertices().keySet().contains(vertex.getKey())) {
        continue;
      } else {
        return false;
      }

    }
    return true;

  }

  @Override
  public String toString()
  {

    return String.valueOf(this.getId());

    /*StringBuilder stringRepresentation = new StringBuilder();

    vertices.forEach(vertex -> stringRepresentation.append(vertex.getId()).append("->"));

    return stringRepresentation.substring(0, stringRepresentation.length() - 2).toString();*/

  }

  public void addVertexToPath(Vertex vertex)
  {
    this.vertices.put(vertex.getId(), vertex);
  }


}
