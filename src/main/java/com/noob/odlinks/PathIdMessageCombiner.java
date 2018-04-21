package com.noob.odlinks;

import org.apache.giraph.combiner.MessageCombiner;
import org.apache.hadoop.io.Text;

public class PathIdMessageCombiner implements MessageCombiner<Text, Text>
{

  private static final String COMMA = ",";

  /**
   * Combine messageToCombine with originalMessage, by modifying
   * originalMessage.  Note that the messageToCombine object
   * may be reused by the infrastructure, therefore, you cannot directly
   * use it or any objects from it in original message
   *
   * @param vertexIndex      Index of the vertex getting these messages
   * @param originalMessage  The first message which we want to combine;
   *                         put the result of combining in this message
   * @param messageToCombine The second message which we want to combine
   *                         (object may be reused - do not reference it or its
   */
  @Override
  public void combine(Text vertexIndex, Text originalMessage, Text messageToCombine)
  {
    if(originalMessage.toString().isEmpty())  originalMessage.set(messageToCombine.toString());
    else  originalMessage.set(COMMA.concat(originalMessage.toString()).concat(COMMA).concat(messageToCombine.toString()));
  }

  /**
   * Get the initial message. When combined with any other message M,
   * the result should be M.
   *
   * @return Initial message
   */
  @Override
  public Text createInitialMessage()
  {
    return new Text();
  }
}
