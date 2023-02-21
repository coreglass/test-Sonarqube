package com.binarywang.spring.starter.wxjava.miniapp.properties;

import com.binarywang.spring.starter.wxjava.miniapp.enums.HttpClientType;
import com.binarywang.spring.starter.wxjava.miniapp.enums.StorageType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import static com.binarywang.spring.starter.wxjava.miniapp.properties.WxMaProperties.PREFIX;

/**
 * 属性配置类.
 *
 * @author <a href="https://github.com/binarywang">Binary Wang</a>
 * created on  2019-08-10
 */
@Data
@ConfigurationProperties(prefix = PREFIX)
public class WxMaProperties {
  public static final String PREFIX = "wx.miniapp";

  /**
   * 设置微信小程序的appid.
   */
  private String appid;

  /**
   * 设置微信小程序的Secret.
   */
  private String secret;

  /**
   * 设置微信小程序消息服务器配置的token.
   */
  private String token;

  /**
   * 设置微信小程序消息服务器配置的EncodingAESKey.
   */
  private String aesKey;

  /**
   * 消息格式，XML或者JSON.
   */
  private String msgDataFormat;

  /**
   * 存储策略
   */
  private final ConfigStorage configStorage = new ConfigStorage();

  @Data
  public static class ConfigStorage {

    /**
     * 存储类型.
     */
    private StorageType type = StorageType.Memory;

    /**
     * 指定key前缀.
     */
    private String keyPrefix = "wa";

    /**
     * redis连接配置.
     */
    @NestedConfigurationProperty
    private final RedisProperties redis = new RedisProperties();

    /**
     * http客户端类型.
     */
    private HttpClientType httpClientType = HttpClientType.HttpClient;

    /**
     * http代理主机.
     */
    private String httpProxyHost;

    /**
     * http代理端口.
     */
    private Integer httpProxyPort;

    /**
     * http代理用户名.
     */
    private String httpProxyUsername;

    /**
     * http代理密码.
     */
    private String httpProxyPassword;

    /**
     * http 请求重试间隔
     * <pre>
     *   {@link cn.binarywang.wx.miniapp.api.impl.BaseWxMaServiceImpl#setRetrySleepMillis(int)}
     * </pre>
     */
    private int retrySleepMillis = 1000;
    /**
     * http 请求最大重试次数
     * <pre>
     *   {@link cn.binarywang.wx.miniapp.api.impl.BaseWxMaServiceImpl#setMaxRetryTimes(int)}
     * </pre>
     */
    private int maxRetryTimes = 5;
  }

}
