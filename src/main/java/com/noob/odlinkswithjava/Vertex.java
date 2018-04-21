package com.noob.odlinkswithjava;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;


public class Vertex
{

  @Getter
  private List<Vertex> neighbors;

  private List<Path> incomingMessages;

  @Getter
  private List<Path> newFoundPathsToSource;

  @Getter
  private List<Path> knowPathsToSource;

  @Getter
  final private String id;

  public Vertex(String id)
  {
    this.id = id;
    neighbors = new ArrayList<>();
    knowPathsToSource = new ArrayList<>();
  }

  @Override
  public String toString()
  {

    StringBuilder vertexRepresentation = new StringBuilder(this.getId());

    vertexRepresentation.append(" : ");

    knowPathsToSource.forEach(knownPath -> vertexRepresentation.append(knownPath.toString()).append(","));

    return vertexRepresentation.substring(0, vertexRepresentation.length() - 1).toString();

  }

  public void addMessage(Path path)
  {

    if (null == incomingMessages) {

      incomingMessages = new ArrayList<>();

    }

    incomingMessages.add(path);

  }

  public void addMessage(List<Path> knowPathsToSource)
  {

    if (null == incomingMessages) {

      incomingMessages = new ArrayList<>();

    }

    incomingMessages.addAll(knowPathsToSource);

  }

  //If a new path was found, return a true value, so that the controlling program can broadcast to its neighbors
  public boolean processMessages()
  {

    boolean foundNewPaths = false;

    if (null == this.incomingMessages) {
      return foundNewPaths;
    }

    for (Path path : this.incomingMessages) {

      boolean pathHasLoop = false;

      //to check for loop, check if the current vertex has already been registered in this path

      if(path.getVertices().keySet().contains(this.id)) break;


      if (!pathHasLoop) {

        //Check if this path exists
        boolean pathAlreadyRegistered = false;
        for (Path knownPath : this.knowPathsToSource) {
          if (path.equals(knownPath)) {
            pathAlreadyRegistered = true;
            break;
          }
        }

        if (!pathAlreadyRegistered) {
          Path newPath = new Path(path);

          newPath.addVertexToPath(this);

          this.knowPathsToSource.add(newPath);

          if(null == this.newFoundPathsToSource)  this.newFoundPathsToSource = new ArrayList<>();

          this.newFoundPathsToSource.add(newPath);

          foundNewPaths = true;
        }


      }

    }

    resetMessages();

    return foundNewPaths;

  }

  private void resetMessages()
  {
    incomingMessages = new ArrayList<>();
  }

  public void resetNewFoundPathsToSource(){
    this.newFoundPathsToSource = new ArrayList<>();
  }

  public void addVertexAsNeighbor(Vertex neighborVertex)
  {

    for (Vertex neighbor : this.getNeighbors()) {
      if (neighbor.getId().equalsIgnoreCase(neighborVertex.getId())) {
        return;
      }
    }

    this.getNeighbors().add(neighborVertex);
    return;

  }

}
