package com.noob.odlinks;

import lombok.Getter;
import redis.clients.jedis.Jedis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class RoutesPrinter
{

  private static final String VERTEX_PREFIX = "vertex_";

  @Getter
  private Jedis jedisConnection;

  public RoutesPrinter(String redisIp, int redisPort)
  {
    jedisConnection = getJedisConnection(redisIp, redisPort);
  }

  private Jedis getJedisConnection(String redisIp, int redisPort)
  {

    if (null == jedisConnection) {
      jedisConnection = new Jedis(redisIp, redisPort);
    }
    return jedisConnection;

  }

  private void deleteJedisConnection()
  {
    if (null != jedisConnection) {
      jedisConnection.close();
    }
  }


  public static void main(String[] args) throws IOException
  {
    RoutesPrinter routesPrinter = new RoutesPrinter(args[0], Integer.parseInt(args[1]));

    BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(new File(args[2])));

    //For all vertex entries in Redis
    for (String vertex : routesPrinter.getJedisConnection().keys(VERTEX_PREFIX.concat("*"))) {
      //Obtain their respective discovered path ids
      for (String pathId : routesPrinter.getJedisConnection().zrange(vertex, Long.MIN_VALUE, Long.MAX_VALUE)) {
        //And discover the actual path that the path id represents
        bufferedWriter.write(vertex.concat(","));
        for (String vertexInPath : routesPrinter.getJedisConnection().zrange(pathId, Long.MIN_VALUE, Long.MAX_VALUE)) {
          bufferedWriter.write(vertexInPath.concat("-"));
        }
        bufferedWriter.write("x");
        bufferedWriter.newLine();
      }
    }
    bufferedWriter.flush();
    bufferedWriter.close();
    routesPrinter.deleteJedisConnection();
  }

}
