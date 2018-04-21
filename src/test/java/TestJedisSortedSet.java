import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.util.Set;

public class TestJedisSortedSet
{

  private Jedis jedisConnection;

  private Jedis getJedisConnection()
  {

    if (null == jedisConnection) {
      jedisConnection = new Jedis("localhost", 6379);
    }
    return jedisConnection;

  }

  @Test
  @Ignore
  public void testJedisZrankForNil(){
    getJedisConnection().zadd("key", 1, "memeber1");
    getJedisConnection().zadd("key", 2, "memeber2");
    getJedisConnection().zadd("key", 3, "memeber3");
    getJedisConnection().zadd("key", 4, "memeber4");
    getJedisConnection().zadd("key", 5, "memeber5");
    getJedisConnection().zadd("key", 6, "memeber6");
    System.out.println(getJedisConnection().zrank("key", "member7"));
    Assert.assertEquals(null, getJedisConnection().zrank("key", "member7"));

  }


  @Test
  @Ignore
  public void testJedisZRangeForOrder(){
    getJedisConnection().zadd("key", 1, "memeber1");
    getJedisConnection().zadd("key", 2, "memeber2");
    getJedisConnection().zadd("key", 3, "memeber3");
    getJedisConnection().zadd("key", 4, "memeber4");
    getJedisConnection().zadd("key", 5, "memeber5");
    getJedisConnection().zadd("key", 6, "memeber6");
    getJedisConnection().zadd("key", 10, "memeber10");
    getJedisConnection().zadd("key", 8, "memeber8");
    getJedisConnection().zadd("key", 9, "memeber9");
    getJedisConnection().zadd("key", 7, "memeber7");


    final Set<String> strings = getJedisConnection().zrange("key", Long.MIN_VALUE, Long.MAX_VALUE);
    strings.add("memeber0");
    strings.add("memeber20");
    strings.forEach(e -> System.out.println(e));
  }
}
