package com.binarywang.spring.starter.wxjava.mp.enums;

/**
 * storage类型.
 *
 * @author <a href="https://github.com/binarywang">Binary Wang</a>
 * created on  2020-08-30
 */
public enum StorageType {
  /**
   * 内存.
   */
  Memory,
  /**
   * redis(JedisClient).
   */
  Jedis,
  /**
   * redis(Redisson).
   */
  Redisson,
  /**
   * redis(RedisTemplate).
   */
  RedisTemplate
}
