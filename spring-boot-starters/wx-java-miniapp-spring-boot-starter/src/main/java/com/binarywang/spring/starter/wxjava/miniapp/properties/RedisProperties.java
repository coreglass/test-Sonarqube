package com.binarywang.spring.starter.wxjava.miniapp.properties;

import lombok.Data;

/**
 * redis 配置.
 *
 * @author <a href="https://github.com/binarywang">Binary Wang</a>
 * created on  2020-08-30
 */
@Data
public class RedisProperties {

  /**
   * 主机地址.不填则从spring容器内获取JedisPool
   */
  private String host;

  /**
   * 端口号.
   */
  private int port = 6379;

  /**
   * 密码.
   */
  private String password;

  /**
   * 超时.
   */
  private int timeout = 2000;

  /**
   * 数据库.
   */
  private int database = 0;

  private Integer maxActive;
  private Integer maxIdle;
  private Integer maxWaitMillis;
  private Integer minIdle;
}
