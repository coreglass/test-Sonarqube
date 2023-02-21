package com.binarywang.spring.starter.wxjava.miniapp.config.storage;

import cn.binarywang.wx.miniapp.config.WxMaConfig;
import cn.binarywang.wx.miniapp.config.impl.WxMaRedisBetterConfigImpl;
import com.binarywang.spring.starter.wxjava.miniapp.properties.RedisProperties;
import com.binarywang.spring.starter.wxjava.miniapp.properties.WxMaProperties;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.redis.JedisWxRedisOps;
import me.chanjar.weixin.common.redis.WxRedisOps;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * @author yl TaoYu
 */
@Configuration
@ConditionalOnProperty(prefix = WxMaProperties.PREFIX + ".config-storage", name = "type", havingValue = "jedis")
@ConditionalOnClass({JedisPool.class, JedisPoolConfig.class})
@RequiredArgsConstructor
public class WxMaInJedisConfigStorageConfiguration extends AbstractWxMaConfigStorageConfiguration {
  private final WxMaProperties properties;
  private final ApplicationContext applicationContext;

  @Bean
  @ConditionalOnMissingBean(WxMaConfig.class)
  public WxMaConfig wxMaConfig() {
    WxMaRedisBetterConfigImpl config = getWxMaRedisBetterConfigImpl();
    return this.config(config, properties);
  }

  private WxMaRedisBetterConfigImpl getWxMaRedisBetterConfigImpl() {
    RedisProperties redisProperties = properties.getConfigStorage().getRedis();
    JedisPool jedisPool;
    if (redisProperties != null && StringUtils.isNotEmpty(redisProperties.getHost())) {
      jedisPool = getJedisPool();
    } else {
      jedisPool = applicationContext.getBean(JedisPool.class);
    }
    WxRedisOps redisOps = new JedisWxRedisOps(jedisPool);
    return new WxMaRedisBetterConfigImpl(redisOps, properties.getConfigStorage().getKeyPrefix());
  }

  private JedisPool getJedisPool() {
    WxMaProperties.ConfigStorage storage = properties.getConfigStorage();
    RedisProperties redis = storage.getRedis();

    JedisPoolConfig config = new JedisPoolConfig();
    if (redis.getMaxActive() != null) {
      config.setMaxTotal(redis.getMaxActive());
    }
    if (redis.getMaxIdle() != null) {
      config.setMaxIdle(redis.getMaxIdle());
    }
    if (redis.getMaxWaitMillis() != null) {
      config.setMaxWaitMillis(redis.getMaxWaitMillis());
    }
    if (redis.getMinIdle() != null) {
      config.setMinIdle(redis.getMinIdle());
    }
    config.setTestOnBorrow(true);
    config.setTestWhileIdle(true);

    return new JedisPool(config, redis.getHost(), redis.getPort(), redis.getTimeout(), redis.getPassword(), redis.getDatabase());
  }
}
