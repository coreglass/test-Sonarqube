package me.chanjar.weixin.cp.tp.service;

import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.cp.bean.WxCpTpUnionidToExternalUseridResult;

/**
 * <pre>
 *  企业微信三方应用ID转换接口
 *
 * </pre>
 *
 * @author cocoa
 */
public interface WxCpTpIdConvertService {

  /**
   * unionid与external_userid的关联
   * <a href="https://developer.work.weixin.qq.com/document/path/95900">查看文档</a>
   *
   * @param unionid     微信客户的unionid
   * @param openid      微信客户的openid
   * @param subjectType 程序或公众号的主体类型： 0表示主体名称是企业的，1表示主体名称是服务商的
   * @throws WxErrorException 。
   */
  WxCpTpUnionidToExternalUseridResult unionidToExternalUserid(String cropId, String unionid, String openid,
                                                              Integer subjectType) throws WxErrorException;

}
