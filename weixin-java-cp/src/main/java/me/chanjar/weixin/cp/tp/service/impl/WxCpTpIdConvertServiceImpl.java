package me.chanjar.weixin.cp.tp.service.impl;

import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.cp.bean.WxCpTpUnionidToExternalUseridResult;
import me.chanjar.weixin.cp.config.WxCpTpConfigStorage;
import me.chanjar.weixin.cp.tp.service.WxCpTpIdConvertService;
import me.chanjar.weixin.cp.tp.service.WxCpTpService;


/**
 * @author cocoa
 */
@RequiredArgsConstructor
public class WxCpTpIdConvertServiceImpl implements WxCpTpIdConvertService {
  private final WxCpTpService mainService;

  @Override
  public WxCpTpUnionidToExternalUseridResult unionidToExternalUserid(String cropId, String unionid, String openid, Integer subjectType) throws WxErrorException {
    JsonObject json = new JsonObject();
    json.addProperty("unionid", unionid);
    json.addProperty("openid", openid);
    if (subjectType != null) {
      json.addProperty("subject_type", subjectType);
    }
    WxCpTpConfigStorage wxCpTpConfigStorage = mainService.getWxCpTpConfigStorage();
    String accessToken = wxCpTpConfigStorage.getAccessToken(cropId);
    String url = wxCpTpConfigStorage.getApiUrl("/cgi-bin/idconvert/unionid_to_external_userid");
    url += "?access_token=" + accessToken;
    String responseContent = this.mainService.post(url, json.toString());
    return WxCpTpUnionidToExternalUseridResult.fromJson(responseContent);
  }


}
