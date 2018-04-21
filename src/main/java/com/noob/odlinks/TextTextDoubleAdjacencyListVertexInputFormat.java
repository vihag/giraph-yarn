package com.noob.odlinks;

import org.apache.giraph.edge.Edge;
import org.apache.giraph.edge.EdgeFactory;
import org.apache.giraph.io.formats.AdjacencyListTextVertexInputFormat;
import org.apache.giraph.io.formats.TextDoubleDoubleAdjacencyListVertexInputFormat;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

public class TextTextDoubleAdjacencyListVertexInputFormat
    extends AdjacencyListTextVertexInputFormat<Text, Text, DoubleWritable>
{

  @Override
  public AdjacencyListTextVertexInputFormat.AdjacencyListTextVertexReader createVertexReader(
      InputSplit split,
      TaskAttemptContext context
  )
  {
    return new TextTextDoubleAdjacencyListVertexReader(null);
  }

  /**
   * Vertex reader used with
   * {@link TextDoubleDoubleAdjacencyListVertexInputFormat}
   */
  protected class TextTextDoubleAdjacencyListVertexReader extends
      AdjacencyListTextVertexReader
  {

    /**
     * Constructor with
     * {@link AdjacencyListTextVertexInputFormat.LineSanitizer}.
     *
     * @param lineSanitizer the sanitizer to use for reading
     */
    public TextTextDoubleAdjacencyListVertexReader(
        LineSanitizer
            lineSanitizer
    )
    {
      super(lineSanitizer);
    }

    @Override
    public Text decodeId(String s)
    {
      return new Text(s);
    }

    @Override
    public Text decodeValue(String s)
    {
      return new Text(s);
    }

    @Override
    public Edge<Text, DoubleWritable> decodeEdge(String s1, String s2)
    {

      Edge<Text, DoubleWritable> edge = null;

      try{
        edge = EdgeFactory.create(
            new Text(s1),
            new DoubleWritable(Double.parseDouble(s2))
        );

      }catch (Exception e){
        System.exit(0);
      }

      return edge;
    }
  }

}
