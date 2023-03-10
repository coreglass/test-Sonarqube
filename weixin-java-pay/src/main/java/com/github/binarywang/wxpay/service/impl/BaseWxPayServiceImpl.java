package com.github.binarywang.wxpay.service.impl;

import com.github.binarywang.utils.qrcode.QrcodeUtils;
import com.github.binarywang.wxpay.bean.WxPayApiData;
import com.github.binarywang.wxpay.bean.coupon.*;
import com.github.binarywang.wxpay.bean.notify.*;
import com.github.binarywang.wxpay.bean.order.WxPayAppOrderResult;
import com.github.binarywang.wxpay.bean.order.WxPayMpOrderResult;
import com.github.binarywang.wxpay.bean.order.WxPayMwebOrderResult;
import com.github.binarywang.wxpay.bean.order.WxPayNativeOrderResult;
import com.github.binarywang.wxpay.bean.request.*;
import com.github.binarywang.wxpay.bean.result.*;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.config.WxPayConfigHolder;
import com.github.binarywang.wxpay.constant.WxPayConstants;
import com.github.binarywang.wxpay.constant.WxPayConstants.SignType;
import com.github.binarywang.wxpay.constant.WxPayConstants.TradeType;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.*;
import com.github.binarywang.wxpay.util.SignUtils;
import com.github.binarywang.wxpay.util.XmlConfig;
import com.github.binarywang.wxpay.util.ZipUtils;
import com.github.binarywang.wxpay.v3.util.AesUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;
import me.chanjar.weixin.common.error.WxRuntimeException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.zip.ZipException;

import static com.github.binarywang.wxpay.constant.WxPayConstants.QUERY_COMMENT_DATE_FORMAT;
import static com.github.binarywang.wxpay.constant.WxPayConstants.TarType;

/**
 * <pre>
 *  ???????????????????????????????????????
 * Created by Binary Wang on 2017-7-8.
 * </pre>
 *
 * @author <a href="https://github.com/binarywang">Binary Wang</a>
 */
public abstract class BaseWxPayServiceImpl implements WxPayService {
  private static final String TOTAL_FUND_COUNT = "?????????????????????";

  private static final Gson GSON = new GsonBuilder().create();

  final Logger log = LoggerFactory.getLogger(this.getClass());

  static ThreadLocal<WxPayApiData> wxApiData = new ThreadLocal<>();


  @Setter
  @Getter
  private EntPayService entPayService = new EntPayServiceImpl(this);

  @Getter
  private final ProfitSharingService profitSharingService = new ProfitSharingServiceImpl(this);

  @Getter
  private final ProfitSharingV3Service profitSharingV3Service = new ProfitSharingV3ServiceImpl(this);

  @Getter
  private final RedpackService redpackService = new RedpackServiceImpl(this);

  @Getter
  private final PayScoreService payScoreService = new PayScoreServiceImpl(this);

  @Getter
  private final EcommerceService ecommerceService = new EcommerceServiceImpl(this);

  @Getter
  private final BusinessCircleService businessCircleService = new BusinessCircleServiceImpl(this);

  @Getter
  private final MerchantMediaService merchantMediaService = new MerchantMediaServiceImpl(this);

  @Getter
  private final MarketingMediaService marketingMediaService = new MarketingMediaServiceImpl(this);

  @Getter
  private final MarketingFavorService marketingFavorService = new MarketingFavorServiceImpl(this);

  @Getter
  private final MarketingBusiFavorService marketingBusiFavorService = new MarketingBusiFavorServiceImpl(this);

  @Getter
  private final WxEntrustPapService wxEntrustPapService = new WxEntrustPapServiceImpl(this);

  @Getter
  private final PartnerTransferService partnerTransferService = new PartnerTransferServiceImpl(this);

  @Getter
  private final PayrollService payrollService = new PayrollServiceImpl(this);

  @Getter
  private final ComplaintService complaintsService = new ComplaintServiceImpl(this);

  @Getter
  private final BankService bankService = new BankServiceImpl(this);

  @Getter
  private final TransferService transferService = new TransferServiceImpl(this);

  @Getter
  private final PartnerPayScoreService partnerPayScoreService = new PartnerPayScoreServiceImpl(this);

  @Getter
  private final MerchantTransferService merchantTransferService = new MerchantTransferServiceImpl(this);

  protected Map<String, WxPayConfig> configMap;

  @Override
  public WxPayConfig getConfig() {
    if (this.configMap.size() == 1) {
      // ???????????????????????????????????????????????????
      return this.configMap.values().iterator().next();
    }
    return this.configMap.get(WxPayConfigHolder.get());
  }

  @Override
  public void setConfig(WxPayConfig config) {
    final String defaultMchId = config.getMchId();
    this.setMultiConfig(ImmutableMap.of(defaultMchId, config), defaultMchId);
  }

  @Override
  public void addConfig(String mchId, WxPayConfig wxPayConfig) {
    synchronized (this) {
      if (this.configMap == null) {
        this.setConfig(wxPayConfig);
      } else {
        WxPayConfigHolder.set(mchId);
        this.configMap.put(mchId, wxPayConfig);
      }
    }
  }

  @Override
  public void removeConfig(String mchId) {
    synchronized (this) {
      if (this.configMap.size() == 1) {
        this.configMap.remove(mchId);
        log.warn("???????????????????????????????????????{}??????????????????setConfig???setMultiConfig????????????", mchId);
        return;
      }
      if (WxPayConfigHolder.get().equals(mchId)) {
        this.configMap.remove(mchId);
        final String defaultMpId = this.configMap.keySet().iterator().next();
        WxPayConfigHolder.set(defaultMpId);
        log.warn("?????????????????????????????????????????????{}????????????????????????", defaultMpId);
        return;
      }
      this.configMap.remove(mchId);
    }
  }

  @Override
  public void setMultiConfig(Map<String, WxPayConfig> wxPayConfigs) {
    this.setMultiConfig(wxPayConfigs, wxPayConfigs.keySet().iterator().next());
  }

  @Override
  public void setMultiConfig(Map<String, WxPayConfig> wxPayConfigs, String defaultMchId) {
    this.configMap = Maps.newHashMap(wxPayConfigs);
    WxPayConfigHolder.set(defaultMchId);
  }

  @Override
  public boolean switchover(String mchId) {
    if (this.configMap.containsKey(mchId)) {
      WxPayConfigHolder.set(mchId);
      return true;
    }
    log.error("?????????????????????{}??????????????????????????????????????????", mchId);
    return false;
  }

  @Override
  public WxPayService switchoverTo(String mchId) {
    if (this.configMap.containsKey(mchId)) {
      WxPayConfigHolder.set(mchId);
      return this;
    }
    throw new WxRuntimeException(String.format("?????????????????????%s??????????????????????????????????????????", mchId));
  }

  @Override
  public String getPayBaseUrl() {
    if (this.getConfig().isUseSandboxEnv()) {
      return this.getConfig().getPayBaseUrl() + "/xdc/apiv2sandbox";
    }

    return this.getConfig().getPayBaseUrl();
  }

  @Override
  public WxPayRefundResult refund(WxPayRefundRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/secapi/pay/refund";
    String responseContent = this.post(url, request.toXML(), true);
    WxPayRefundResult result = BaseWxPayResult.fromXML(responseContent, WxPayRefundResult.class);
    result.composeRefundCoupons();
    result.checkResult(this, request.getSignType(), true);
    return result;
  }

  @Override
  public WxPayRefundResult refundV2(WxPayRefundRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/secapi/pay/refundv2";
    String responseContent = this.post(url, request.toXML(), true);
    WxPayRefundResult result = BaseWxPayResult.fromXML(responseContent, WxPayRefundResult.class);
    result.composePromotionDetails();
    result.checkResult(this, request.getSignType(), true);
    return result;
  }

  @Override
  public WxPayRefundV3Result refundV3(WxPayRefundV3Request request) throws WxPayException {
    String url = String.format("%s/v3/refund/domestic/refunds", this.getPayBaseUrl());
    String response = this.postV3(url, GSON.toJson(request));
    return GSON.fromJson(response, WxPayRefundV3Result.class);
  }

  @Override
  public WxPayRefundQueryResult refundQuery(String transactionId, String outTradeNo, String outRefundNo, String refundId)
    throws WxPayException {
    WxPayRefundQueryRequest request = new WxPayRefundQueryRequest();
    request.setOutTradeNo(StringUtils.trimToNull(outTradeNo));
    request.setTransactionId(StringUtils.trimToNull(transactionId));
    request.setOutRefundNo(StringUtils.trimToNull(outRefundNo));
    request.setRefundId(StringUtils.trimToNull(refundId));

    return this.refundQuery(request);
  }

  @Override
  public WxPayRefundQueryResult refundQuery(WxPayRefundQueryRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/pay/refundquery";
    String responseContent = this.post(url, request.toXML(), false);
    WxPayRefundQueryResult result = BaseWxPayResult.fromXML(responseContent, WxPayRefundQueryResult.class);
    result.composeRefundRecords();
    result.checkResult(this, request.getSignType(), true);
    return result;
  }

  @Override
  public WxPayRefundQueryResult refundQueryV2(WxPayRefundQueryRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/pay/refundqueryv2";
    String responseContent = this.post(url, request.toXML(), false);
    WxPayRefundQueryResult result = BaseWxPayResult.fromXML(responseContent, WxPayRefundQueryResult.class);
    result.composePromotionDetails();
    result.checkResult(this, request.getSignType(), true);
    return result;
  }

  @Override
  public WxPayRefundQueryV3Result refundQueryV3(String outRefundNo) throws WxPayException {
    String url = String.format("%s/v3/refund/domestic/refunds/%s", this.getPayBaseUrl(), outRefundNo);
    String response = this.getV3(url);
    return GSON.fromJson(response, WxPayRefundQueryV3Result.class);
  }

  @Override
  public WxPayRefundQueryV3Result refundQueryV3(WxPayRefundQueryV3Request request) throws WxPayException {
    String url = String.format("%s/v3/refund/domestic/refunds/%s", this.getPayBaseUrl(), request.getOutRefundNo());
    String response = this.getV3(url);
    return GSON.fromJson(response, WxPayRefundQueryV3Result.class);
  }

  @Override
  public WxPayOrderNotifyResult parseOrderNotifyResult(String xmlData) throws WxPayException {
    return this.parseOrderNotifyResult(xmlData, null);
  }

  @Override
  public WxPayOrderNotifyResult parseOrderNotifyResult(String xmlData, String signType) throws WxPayException {
    try {
      log.debug("???????????????????????????????????????{}", xmlData);
      WxPayOrderNotifyResult result = WxPayOrderNotifyResult.fromXML(xmlData);
      if (signType == null) {
        if (result.getSignType() != null) {
          // ??????????????????????????????signType?????????????????????????????????
          signType = result.getSignType();
        } else if (configMap.get(result.getMchId()).getSignType() != null) {
          // ???????????????signType?????????????????????????????????
          signType = configMap.get(result.getMchId()).getSignType();
          this.switchover(result.getMchId());
        }
      }

      log.debug("???????????????????????????????????????????????????{}", result);
      result.checkResult(this, signType, false);
      return result;
    } catch (WxPayException e) {
      throw e;
    } catch (Exception e) {
      throw new WxPayException("???????????????", e);
    }
  }

  /**
   * ??????????????????
   *
   * @param header ???????????????
   * @param data   ????????????
   * @return true:???????????? false:???????????????
   */
  private boolean verifyNotifySign(SignatureHeader header, String data) {
    String beforeSign = String.format("%s\n%s\n%s\n",
      header.getTimeStamp(),
      header.getNonce(),
      data);
    return this.getConfig().getVerifier().verify(header.getSerial(),
      beforeSign.getBytes(StandardCharsets.UTF_8), header.getSignature());
  }

  @Override
  public WxPayOrderNotifyV3Result parseOrderNotifyV3Result(String notifyData, SignatureHeader header) throws WxPayException {
    if (Objects.nonNull(header) && !this.verifyNotifySign(header, notifyData)) {
      throw new WxPayException("???????????????????????????????????????");
    }
    OriginNotifyResponse response = GSON.fromJson(notifyData, OriginNotifyResponse.class);
    OriginNotifyResponse.Resource resource = response.getResource();
    String cipherText = resource.getCiphertext();
    String associatedData = resource.getAssociatedData();
    String nonce = resource.getNonce();
    String apiV3Key = this.getConfig().getApiV3Key();
    try {
      String result = AesUtils.decryptToString(associatedData, nonce, cipherText, apiV3Key);
      WxPayOrderNotifyV3Result.DecryptNotifyResult decryptNotifyResult = GSON.fromJson(result, WxPayOrderNotifyV3Result.DecryptNotifyResult.class);
      WxPayOrderNotifyV3Result notifyResult = new WxPayOrderNotifyV3Result();
      notifyResult.setRawData(response);
      notifyResult.setResult(decryptNotifyResult);
      return notifyResult;
    } catch (GeneralSecurityException | IOException e) {
      throw new WxPayException("?????????????????????", e);
    }
  }

  @Override
  public CombineNotifyResult parseCombineNotifyResult(String notifyData, SignatureHeader header) throws WxPayException {
    if (Objects.nonNull(header) && !this.verifyNotifySign(header, notifyData)) {
      throw new WxPayException("???????????????????????????????????????");
    }
    OriginNotifyResponse response = GSON.fromJson(notifyData, OriginNotifyResponse.class);
    OriginNotifyResponse.Resource resource = response.getResource();
    String cipherText = resource.getCiphertext();
    String associatedData = resource.getAssociatedData();
    String nonce = resource.getNonce();
    String apiV3Key = this.getConfig().getApiV3Key();
    try {
      String result = AesUtils.decryptToString(associatedData, nonce, cipherText, apiV3Key);
      CombineNotifyResult.DecryptNotifyResult decryptNotifyResult = GSON.fromJson(result, CombineNotifyResult.DecryptNotifyResult.class);
      CombineNotifyResult notifyResult = new CombineNotifyResult();
      notifyResult.setRawData(response);
      notifyResult.setResult(decryptNotifyResult);
      return notifyResult;
    } catch (GeneralSecurityException | IOException e) {
      throw new WxPayException("?????????????????????", e);
    }
  }

  @Override
  public WxPayRefundNotifyResult parseRefundNotifyResult(String xmlData) throws WxPayException {
    try {
      log.debug("???????????????????????????????????????{}", xmlData);
      WxPayRefundNotifyResult result;
      if (XmlConfig.fastMode) {
        result = BaseWxPayResult.fromXML(xmlData, WxPayRefundNotifyResult.class);
        this.switchover(result.getMchId());
        result.decryptReqInfo(this.getConfig().getMchKey());
      } else {
        result = WxPayRefundNotifyResult.fromXML(xmlData, this.getConfig().getMchKey());
      }
      log.debug("???????????????????????????????????????????????????{}", result);
      return result;
    } catch (Exception e) {
      throw new WxPayException("???????????????" + e.getMessage(), e);
    }
  }

  @Override
  public WxPayRefundNotifyV3Result parseRefundNotifyV3Result(String notifyData, SignatureHeader header) throws WxPayException {
    if (Objects.nonNull(header) && !this.verifyNotifySign(header, notifyData)) {
      throw new WxPayException("???????????????????????????????????????");
    }
    OriginNotifyResponse response = GSON.fromJson(notifyData, OriginNotifyResponse.class);
    OriginNotifyResponse.Resource resource = response.getResource();
    String cipherText = resource.getCiphertext();
    String associatedData = resource.getAssociatedData();
    String nonce = resource.getNonce();
    String apiV3Key = this.getConfig().getApiV3Key();
    try {
      String result = AesUtils.decryptToString(associatedData, nonce, cipherText, apiV3Key);
      WxPayRefundNotifyV3Result.DecryptNotifyResult decryptNotifyResult = GSON.fromJson(result, WxPayRefundNotifyV3Result.DecryptNotifyResult.class);
      WxPayRefundNotifyV3Result notifyResult = new WxPayRefundNotifyV3Result();
      notifyResult.setRawData(response);
      notifyResult.setResult(decryptNotifyResult);
      return notifyResult;
    } catch (GeneralSecurityException | IOException e) {
      throw new WxPayException("?????????????????????", e);
    }
  }

  @Override
  public WxScanPayNotifyResult parseScanPayNotifyResult(String xmlData, @Deprecated String signType) throws WxPayException {
    try {
      log.debug("???????????????????????????????????????{}", xmlData);
      WxScanPayNotifyResult result = BaseWxPayResult.fromXML(xmlData, WxScanPayNotifyResult.class);
      this.switchover(result.getMchId());
      log.debug("?????????????????????????????????????????????{}", result);
      result.checkResult(this, this.getConfig().getSignType(), false);
      return result;
    } catch (WxPayException e) {
      throw e;
    } catch (Exception e) {
      throw new WxPayException("???????????????" + e.getMessage(), e);
    }
  }

  @Override
  public WxScanPayNotifyResult parseScanPayNotifyResult(String xmlData) throws WxPayException {
//    final String signType = this.getConfig().getSignType();
    return this.parseScanPayNotifyResult(xmlData, null);
  }

  @Override
  public WxPayOrderQueryResult queryOrder(String transactionId, String outTradeNo) throws WxPayException {
    WxPayOrderQueryRequest request = new WxPayOrderQueryRequest();
    request.setOutTradeNo(StringUtils.trimToNull(outTradeNo));
    request.setTransactionId(StringUtils.trimToNull(transactionId));

    return this.queryOrder(request);
  }

  @Override
  public WxPayOrderQueryResult queryOrder(WxPayOrderQueryRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/pay/orderquery";
    String responseContent = this.post(url, request.toXML(), false);
    if (StringUtils.isBlank(responseContent)) {
      throw new WxPayException("???????????????");
    }

    WxPayOrderQueryResult result = BaseWxPayResult.fromXML(responseContent, WxPayOrderQueryResult.class);
    result.composeCoupons();
    result.checkResult(this, request.getSignType(), true);
    return result;
  }

  @Override
  public WxPayOrderQueryV3Result queryOrderV3(String transactionId, String outTradeNo) throws WxPayException {
    WxPayOrderQueryV3Request request = new WxPayOrderQueryV3Request();
    request.setOutTradeNo(StringUtils.trimToNull(outTradeNo));
    request.setTransactionId(StringUtils.trimToNull(transactionId));
    return this.queryOrderV3(request);
  }

  @Override
  public WxPayOrderQueryV3Result queryOrderV3(WxPayOrderQueryV3Request request) throws WxPayException {
    if (StringUtils.isBlank(request.getMchid())) {
      request.setMchid(this.getConfig().getMchId());
    }
    String url = String.format("%s/v3/pay/transactions/out-trade-no/%s", this.getPayBaseUrl(), request.getOutTradeNo());
    if (Objects.isNull(request.getOutTradeNo())) {
      url = String.format("%s/v3/pay/transactions/id/%s", this.getPayBaseUrl(), request.getTransactionId());
    }
    String query = String.format("?mchid=%s", request.getMchid());
    String response = this.getV3(url + query);
    return GSON.fromJson(response, WxPayOrderQueryV3Result.class);
  }

  @Override
  public CombineQueryResult queryCombine(String combineOutTradeNo) throws WxPayException {
    String url = String.format("%s/v3/combine-transactions/out-trade-no/%s", this.getPayBaseUrl(), combineOutTradeNo);
    String response = this.getV3(url);
    return GSON.fromJson(response, CombineQueryResult.class);
  }

  @Override
  public WxPayOrderCloseResult closeOrder(String outTradeNo) throws WxPayException {
    if (StringUtils.isBlank(outTradeNo)) {
      throw new WxPayException("out_trade_no????????????");
    }

    WxPayOrderCloseRequest request = new WxPayOrderCloseRequest();
    request.setOutTradeNo(StringUtils.trimToNull(outTradeNo));

    return this.closeOrder(request);
  }

  @Override
  public WxPayOrderCloseResult closeOrder(WxPayOrderCloseRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/pay/closeorder";
    String responseContent = this.post(url, request.toXML(), false);
    WxPayOrderCloseResult result = BaseWxPayResult.fromXML(responseContent, WxPayOrderCloseResult.class);
    result.checkResult(this, request.getSignType(), true);

    return result;
  }

  @Override
  public void closeOrderV3(String outTradeNo) throws WxPayException {
    if (StringUtils.isBlank(outTradeNo)) {
      throw new WxPayException("out_trade_no????????????");
    }
    WxPayOrderCloseV3Request request = new WxPayOrderCloseV3Request();
    request.setOutTradeNo(StringUtils.trimToNull(outTradeNo));
    this.closeOrderV3(request);
  }

  @Override
  public void closeOrderV3(WxPayOrderCloseV3Request request) throws WxPayException {
    if (StringUtils.isBlank(request.getMchid())) {
      request.setMchid(this.getConfig().getMchId());
    }
    String url = String.format("%s/v3/pay/transactions/out-trade-no/%s/close", this.getPayBaseUrl(), request.getOutTradeNo());
    this.postV3(url, GSON.toJson(request));
  }

  @Override
  public void closeCombine(CombineCloseRequest request) throws WxPayException {
    String url = String.format("%s/v3/combine-transactions/out-trade-no/%s/close", this.getPayBaseUrl(), request.getCombineOutTradeNo());
    this.postV3(url, GSON.toJson(request));
  }

  @Override
  public <T> T createOrder(WxPayUnifiedOrderRequest request) throws WxPayException {
    WxPayUnifiedOrderResult unifiedOrderResult = this.unifiedOrder(request);
    String prepayId = unifiedOrderResult.getPrepayId();
    if (StringUtils.isBlank(prepayId)) {
      throw new WxPayException(String.format("????????????prepay id?????????????????? '%s'????????????%s???",
        unifiedOrderResult.getErrCode(), unifiedOrderResult.getErrCodeDes()));
    }

    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
    String nonceStr = unifiedOrderResult.getNonceStr();
    switch (request.getTradeType()) {
      case TradeType.MWEB: {
        return (T) new WxPayMwebOrderResult(unifiedOrderResult.getMwebUrl());
      }

      case TradeType.NATIVE: {
        return (T) new WxPayNativeOrderResult(unifiedOrderResult.getCodeURL());
      }

      case TradeType.APP: {
        // APP???????????????????????????????????????????????????APPID????????????????????????APP??????????????????
        String appId = unifiedOrderResult.getAppid();
        if (StringUtils.isNotEmpty(unifiedOrderResult.getSubAppId())) {
          appId = unifiedOrderResult.getSubAppId();
        }

        Map<String, String> configMap = new HashMap<>(8);
        // ???map??????????????????sdk?????????????????????,??????????????????timestamp?????????10???,???????????????????????????
        String partnerId = unifiedOrderResult.getMchId();
        if (StringUtils.isNotEmpty(unifiedOrderResult.getSubMchId())) {
          partnerId = unifiedOrderResult.getSubMchId();
        }

        configMap.put("prepayid", prepayId);
        configMap.put("partnerid", partnerId);
        String packageValue = "Sign=WXPay";
        configMap.put("package", packageValue);
        configMap.put("timestamp", timestamp);
        configMap.put("noncestr", nonceStr);
        configMap.put("appid", appId);

        final WxPayAppOrderResult result = WxPayAppOrderResult.builder()
          .sign(SignUtils.createSign(configMap, request.getSignType(), this.getConfig().getMchKey(), null))
          .prepayId(prepayId)
          .partnerId(partnerId)
          .appId(appId)
          .packageValue(packageValue)
          .timeStamp(timestamp)
          .nonceStr(nonceStr)
          .build();
        return (T) result;
      }

      case TradeType.JSAPI: {
        String signType = request.getSignType();
        if (signType == null) {
          signType = SignType.MD5;
        }

        String appid = unifiedOrderResult.getAppid();
        if (StringUtils.isNotEmpty(unifiedOrderResult.getSubAppId())) {
          appid = unifiedOrderResult.getSubAppId();
        }

        WxPayMpOrderResult payResult = WxPayMpOrderResult.builder()
          .appId(appid)
          .timeStamp(timestamp)
          .nonceStr(nonceStr)
          .packageValue("prepay_id=" + prepayId)
          .signType(signType)
          .build();

        payResult.setPaySign(SignUtils.createSign(payResult, signType, this.getConfig().getMchKey(), null));
        return (T) payResult;
      }

      default: {
        throw new WxPayException("???????????????????????????");
      }
    }

  }

  @Override
  public <T> T createOrder(TradeType.Specific<T> specificTradeType, WxPayUnifiedOrderRequest request) throws WxPayException {
    if (specificTradeType == null) {
      throw new IllegalArgumentException("specificTradeType ????????? null");
    }
    request.setTradeType(specificTradeType.getType());
    return createOrder(request);
  }

  @Override
  public WxPayUnifiedOrderResult unifiedOrder(WxPayUnifiedOrderRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/pay/unifiedorder";
    String responseContent = this.post(url, request.toXML(), false);
    WxPayUnifiedOrderResult result = BaseWxPayResult.fromXML(responseContent, WxPayUnifiedOrderResult.class);
    result.checkResult(this, request.getSignType(), true);
    return result;
  }

  @Override
  public <T> T createOrderV3(TradeTypeEnum tradeType, WxPayUnifiedOrderV3Request request) throws WxPayException {
    WxPayUnifiedOrderV3Result result = this.unifiedOrderV3(tradeType, request);
    return result.getPayInfo(tradeType, request.getAppid(), request.getMchid(), this.getConfig().getPrivateKey());
  }

  @Override
  public WxPayUnifiedOrderV3Result unifiedOrderV3(TradeTypeEnum tradeType, WxPayUnifiedOrderV3Request request) throws WxPayException {
    if (StringUtils.isBlank(request.getAppid())) {
      request.setAppid(this.getConfig().getAppId());
    }
    if (StringUtils.isBlank(request.getMchid())) {
      request.setMchid(this.getConfig().getMchId());
    }
    if (StringUtils.isBlank(request.getNotifyUrl())) {
      request.setNotifyUrl(this.getConfig().getNotifyUrl());
    }

    String url = this.getPayBaseUrl() + tradeType.getPartnerUrl();
    String response = this.postV3(url, GSON.toJson(request));
    return GSON.fromJson(response, WxPayUnifiedOrderV3Result.class);
  }

  @Override
  public CombineTransactionsResult combine(TradeTypeEnum tradeType, CombineTransactionsRequest request) throws WxPayException {
    if (StringUtils.isBlank(request.getCombineAppid())) {
      request.setCombineAppid(this.getConfig().getAppId());
    }
    if (StringUtils.isBlank(request.getCombineMchid())) {
      request.setCombineMchid(this.getConfig().getMchId());
    }
    String url = this.getPayBaseUrl() + tradeType.getCombineUrl();
    String response = this.postV3(url, GSON.toJson(request));
    return GSON.fromJson(response, CombineTransactionsResult.class);
  }

  @Override
  public <T> T combineTransactions(TradeTypeEnum tradeType, CombineTransactionsRequest request) throws WxPayException {
    CombineTransactionsResult result = this.combine(tradeType, request);
    return result.getPayInfo(tradeType, request.getCombineAppid(), request.getCombineAppid(), this.getConfig().getPrivateKey());
  }

  @Override
  @Deprecated
  public Map<String, String> getPayInfo(WxPayUnifiedOrderRequest request) throws WxPayException {
    WxPayUnifiedOrderResult unifiedOrderResult = this.unifiedOrder(request);
    String prepayId = unifiedOrderResult.getPrepayId();
    if (StringUtils.isBlank(prepayId)) {
      throw new WxRuntimeException(String.format("????????????prepay id?????????????????? '%s'????????????%s???",
        unifiedOrderResult.getErrCode(), unifiedOrderResult.getErrCodeDes()));
    }

    Map<String, String> payInfo = new HashMap<>();
    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
    String nonceStr = unifiedOrderResult.getNonceStr();
    if (TradeType.NATIVE.equals(request.getTradeType())) {
      payInfo.put("codeUrl", unifiedOrderResult.getCodeURL());
    } else if (TradeType.APP.equals(request.getTradeType())) {
      // APP???????????????????????????????????????????????????APPID????????????????????????APP??????????????????
      String appId = getConfig().getAppId();
      Map<String, String> configMap = new HashMap<>();
      // ???map??????????????????sdk?????????????????????,??????????????????timestamp?????????10???,???????????????????????????
      String partnerId = getConfig().getMchId();
      configMap.put("prepayid", prepayId);
      configMap.put("partnerid", partnerId);
      String packageValue = "Sign=WXPay";
      configMap.put("package", packageValue);
      configMap.put("timestamp", timestamp);
      configMap.put("noncestr", nonceStr);
      configMap.put("appid", appId);
      // ???map???????????????????????????????????????
      payInfo.put("sign", SignUtils.createSign(configMap, request.getSignType(), this.getConfig().getMchKey(), null));
      payInfo.put("prepayId", prepayId);
      payInfo.put("partnerId", partnerId);
      payInfo.put("appId", appId);
      payInfo.put("packageValue", packageValue);
      payInfo.put("timeStamp", timestamp);
      payInfo.put("nonceStr", nonceStr);
    } else if (TradeType.JSAPI.equals(request.getTradeType())) {
      payInfo.put("appId", unifiedOrderResult.getAppid());
      // ????????????????????????????????????jssdk??????????????????timestamp?????????????????????????????????????????????????????????????????????timeStamp???????????????????????????S??????
      payInfo.put("timeStamp", timestamp);
      payInfo.put("nonceStr", nonceStr);
      payInfo.put("package", "prepay_id=" + prepayId);
      payInfo.put("signType", request.getSignType());
      payInfo.put("paySign", SignUtils.createSign(payInfo, request.getSignType(), this.getConfig().getMchKey(), null));
    }

    return payInfo;
  }

  @Override
  public byte[] createScanPayQrcodeMode1(String productId, File logoFile, Integer sideLength) {
    String content = this.createScanPayQrcodeMode1(productId);
    return this.createQrcode(content, logoFile, sideLength);
  }

  @Override
  public String createScanPayQrcodeMode1(String productId) {
    //weixin://wxpay/bizpayurl?sign=XXXXX&appid=XXXXX&mch_id=XXXXX&product_id=XXXXXX&time_stamp=XXXXXX&nonce_str=XXXXX
    StringBuilder codeUrl = new StringBuilder("weixin://wxpay/bizpayurl?");
    Map<String, String> params = Maps.newHashMap();
    params.put("appid", this.getConfig().getAppId());
    params.put("mch_id", this.getConfig().getMchId());
    params.put("product_id", productId);
    //??????????????????10?????????
    params.put("time_stamp", String.valueOf(System.currentTimeMillis() / 1000));
    params.put("nonce_str", String.valueOf(System.currentTimeMillis()));

    String sign = SignUtils.createSign(params, SignType.MD5, this.getConfig().getMchKey(), null);
    params.put("sign", sign);

    for (String key : params.keySet()) {
      codeUrl.append(key).append("=").append(params.get(key)).append("&");
    }

    String content = codeUrl.toString().substring(0, codeUrl.length() - 1);
    log.debug("???????????????????????????????????????URL:{}", content);
    return content;
  }

  @Override
  public byte[] createScanPayQrcodeMode2(String codeUrl, File logoFile, Integer sideLength) {
    return this.createQrcode(codeUrl, logoFile, sideLength);
  }

  private byte[] createQrcode(String content, File logoFile, Integer sideLength) {
    if (sideLength == null || sideLength < 1) {
      return QrcodeUtils.createQrcode(content, logoFile);
    }

    return QrcodeUtils.createQrcode(content, sideLength, logoFile);
  }

  @Override
  public void report(WxPayReportRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/payitil/report";
    String responseContent = this.post(url, request.toXML(), false);
    WxPayCommonResult result = BaseWxPayResult.fromXML(responseContent, WxPayCommonResult.class);
    result.checkResult(this, request.getSignType(), true);
  }

  @Override
  public String downloadRawBill(String billDate, String billType, String tarType, String deviceInfo)
    throws WxPayException {
    return this.downloadRawBill(this.buildDownloadBillRequest(billDate, billType, tarType, deviceInfo));
  }

  @Override
  public WxPayBillResult downloadBill(String billDate, String billType, String tarType, String deviceInfo)
    throws WxPayException {
    return this.downloadBill(this.buildDownloadBillRequest(billDate, billType, tarType, deviceInfo));
  }

  private WxPayDownloadBillRequest buildDownloadBillRequest(String billDate, String billType, String tarType,
                                                            String deviceInfo) {
    WxPayDownloadBillRequest request = new WxPayDownloadBillRequest();
    request.setBillType(billType);
    request.setBillDate(billDate);
    request.setTarType(tarType);
    request.setDeviceInfo(deviceInfo);
    return request;
  }

  @Override
  public WxPayBillResult downloadBill(WxPayDownloadBillRequest request) throws WxPayException {
    String responseContent = this.downloadRawBill(request);

    if (StringUtils.isEmpty(responseContent)) {
      return null;
    }

    return this.handleBill(request.getBillType(), responseContent);
  }

  @Override
  public String downloadRawBill(WxPayDownloadBillRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/pay/downloadbill";

    String responseContent;
    if (TarType.GZIP.equals(request.getTarType())) {
      responseContent = this.handleGzipBill(url, request.toXML());
    } else {
      responseContent = this.post(url, request.toXML(), false);
      if (responseContent.startsWith("<")) {
        throw WxPayException.from(BaseWxPayResult.fromXML(responseContent, WxPayCommonResult.class));
      }
    }
    return responseContent;
  }

  private WxPayBillResult handleBill(String billType, String responseContent) {
    return WxPayBillResult.fromRawBillResultString(responseContent, billType);
  }

  private String handleGzipBill(String url, String requestStr) throws WxPayException {
    try {
      byte[] responseBytes = this.postForBytes(url, requestStr, false);
      Path tempDirectory = Files.createTempDirectory("bill");
      Path path = Paths.get(tempDirectory.toString(), System.currentTimeMillis() + ".gzip");
      Files.write(path, responseBytes);
      try {
        List<String> allLines = Files.readAllLines(ZipUtils.unGzip(path.toFile()).toPath(), StandardCharsets.UTF_8);
        return Joiner.on("\n").join(allLines);
      } catch (ZipException e) {
        if (e.getMessage().contains("Not in GZIP format")) {
          throw WxPayException.from(BaseWxPayResult.fromXML(new String(responseBytes, StandardCharsets.UTF_8),
            WxPayCommonResult.class));
        } else {
          throw new WxPayException("??????zip???????????????", e);
        }
      }
    } catch (Exception e) {
      throw new WxPayException("?????????????????????????????????", e);
    }
  }

  @Override
  public WxPayFundFlowResult downloadFundFlow(String billDate, String accountType, String tarType) throws WxPayException {

    WxPayDownloadFundFlowRequest request = new WxPayDownloadFundFlowRequest();
    request.setBillDate(billDate);
    request.setAccountType(accountType);
    request.setTarType(tarType);

    return this.downloadFundFlow(request);
  }

  @Override
  public WxPayFundFlowResult downloadFundFlow(WxPayDownloadFundFlowRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/pay/downloadfundflow";

    String responseContent;
    if (TarType.GZIP.equals(request.getTarType())) {
      responseContent = this.handleGzipFundFlow(url, request.toXML());
    } else {
      responseContent = this.post(url, request.toXML(), true);
      if (responseContent.startsWith("<")) {
        throw WxPayException.from(BaseWxPayResult.fromXML(responseContent, WxPayCommonResult.class));
      }
    }

    return this.handleFundFlow(responseContent);
  }

  private String handleGzipFundFlow(String url, String requestStr) throws WxPayException {
    try {
      byte[] responseBytes = this.postForBytes(url, requestStr, true);
      Path tempDirectory = Files.createTempDirectory("fundFlow");
      Path path = Paths.get(tempDirectory.toString(), System.currentTimeMillis() + ".gzip");
      Files.write(path, responseBytes);

      try {
        List<String> allLines = Files.readAllLines(ZipUtils.unGzip(path.toFile()).toPath(), StandardCharsets.UTF_8);
        return Joiner.on("\n").join(allLines);
      } catch (ZipException e) {
        if (e.getMessage().contains("Not in GZIP format")) {
          throw WxPayException.from(BaseWxPayResult.fromXML(new String(responseBytes, StandardCharsets.UTF_8),
            WxPayCommonResult.class));
        } else {
          throw new WxPayException("??????zip????????????", e);
        }
      }
    } catch (WxPayException wxPayException) {
      throw wxPayException;
    } catch (Exception e) {
      throw new WxPayException("??????zip????????????", e);
    }
  }

  private WxPayFundFlowResult handleFundFlow(String responseContent) {
    WxPayFundFlowResult wxPayFundFlowResult = new WxPayFundFlowResult();

    String listStr = "";
    String objStr = "";

    if (StringUtils.isNotBlank(responseContent) && responseContent.contains(TOTAL_FUND_COUNT)) {
      listStr = responseContent.substring(0, responseContent.indexOf(TOTAL_FUND_COUNT));
      objStr = responseContent.substring(responseContent.indexOf(TOTAL_FUND_COUNT));
    }
    /*
     * ????????????:2018-02-01 04:21:23 ????????????????????????:50000305742018020103387128253 ??????????????????:1900009231201802015884652186 ????????????:??????
     * ????????????:?????? ????????????:?????? ?????????????????????:0.02 ?????????????????????:0.17 ???????????????????????????:system ??????:?????? ???????????????:REF4200000068201801293084726067
     * ??????????????????????????????
     */
    List<WxPayFundFlowBaseResult> wxPayFundFlowBaseResultList = new LinkedList<>();
    // ?????????
    String newStr = listStr.replaceAll(",", " ");
    // ????????????
    String[] tempStr = newStr.split("`");
    // ????????????
    String[] t = tempStr[0].split(" ");
    // ??????????????????
    int j = tempStr.length / t.length;
    // ??????????????????
    int k = 1;
    for (int i = 0; i < j; i++) {
      WxPayFundFlowBaseResult wxPayFundFlowBaseResult = new WxPayFundFlowBaseResult();

      wxPayFundFlowBaseResult.setBillingTime(tempStr[k].trim());
      wxPayFundFlowBaseResult.setBizTransactionId(tempStr[k + 1].trim());
      wxPayFundFlowBaseResult.setFundFlowId(tempStr[k + 2].trim());
      wxPayFundFlowBaseResult.setBizName(tempStr[k + 3].trim());
      wxPayFundFlowBaseResult.setBizType(tempStr[k + 4].trim());
      wxPayFundFlowBaseResult.setFinancialType(tempStr[k + 5].trim());
      wxPayFundFlowBaseResult.setFinancialFee(tempStr[k + 6].trim());
      wxPayFundFlowBaseResult.setAccountBalance(tempStr[k + 7].trim());
      wxPayFundFlowBaseResult.setFundApplicant(tempStr[k + 8].trim());
      wxPayFundFlowBaseResult.setMemo(tempStr[k + 9].trim());
      wxPayFundFlowBaseResult.setBizVoucherId(tempStr[k + 10].trim());

      wxPayFundFlowBaseResultList.add(wxPayFundFlowBaseResult);
      k += t.length;
    }
    wxPayFundFlowResult.setWxPayFundFlowBaseResultList(wxPayFundFlowBaseResultList);

    /*
     * ?????????????????????,????????????,????????????,????????????,???????????? `20.0,`17.0,`0.35,`3.0,`0.18
     * ??????????????????????????????
     */
    String totalStr = objStr.replaceAll(",", " ");
    String[] totalTempStr = totalStr.split("`");
    wxPayFundFlowResult.setTotalRecord(totalTempStr[1]);
    wxPayFundFlowResult.setIncomeRecord(totalTempStr[2]);
    wxPayFundFlowResult.setIncomeAmount(totalTempStr[3]);
    wxPayFundFlowResult.setExpenditureRecord(totalTempStr[4]);
    wxPayFundFlowResult.setExpenditureAmount(totalTempStr[5]);

    return wxPayFundFlowResult;

  }

  @Override
  public WxPayApplyBillV3Result applyTradeBill(WxPayApplyTradeBillV3Request request) throws WxPayException {
    String url;
    if (StringUtils.isBlank(request.getTarType())) {
      url = String.format("%s/v3/bill/tradebill?bill_date=%s&bill_type=%s", this.getPayBaseUrl(), request.getBillDate(), request.getBillType());
    } else {
      url = String.format("%s/v3/bill/tradebill?bill_date=%s&bill_type=%s&tar_type=%s", this.getPayBaseUrl(), request.getBillDate(), request.getBillType(), request.getTarType());
    }
    String response = this.getV3(url);
    return GSON.fromJson(response, WxPayApplyBillV3Result.class);
  }

  @Override
  public WxPayApplyBillV3Result applyFundFlowBill(WxPayApplyFundFlowBillV3Request request) throws WxPayException {
    String url;
    if (StringUtils.isBlank(request.getTarType())) {
      url = String.format("%s/v3/bill/fundflowbill?bill_date=%s&bill_type=%s", this.getPayBaseUrl(), request.getBillDate(), request.getAccountType());
    } else {
      url = String.format("%s/v3/bill/fundflowbill?bill_date=%s&bill_type=%s&tar_type=%s", this.getPayBaseUrl(), request.getBillDate(), request.getAccountType(), request.getTarType());
    }
    String response = this.getV3(url);
    return GSON.fromJson(response, WxPayApplyBillV3Result.class);
  }

  @Override
  public InputStream downloadBill(String url) throws WxPayException {
    return this.downloadV3(url);
  }

  @Override
  public WxPayMicropayResult micropay(WxPayMicropayRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/pay/micropay";
    String responseContent = this.post(url, request.toXML(), false);
    WxPayMicropayResult result = BaseWxPayResult.fromXML(responseContent, WxPayMicropayResult.class);
    result.checkResult(this, request.getSignType(), true);
    return result;
  }

  @Override
  public WxPayOrderReverseResult reverseOrder(WxPayOrderReverseRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/secapi/pay/reverse";
    String responseContent = this.post(url, request.toXML(), true);
    WxPayOrderReverseResult result = BaseWxPayResult.fromXML(responseContent, WxPayOrderReverseResult.class);
    result.checkResult(this, request.getSignType(), true);
    return result;
  }

  @Override
  public String shorturl(WxPayShorturlRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/tools/shorturl";
    String responseContent = this.post(url, request.toXML(), false);
    WxPayShorturlResult result = BaseWxPayResult.fromXML(responseContent, WxPayShorturlResult.class);
    result.checkResult(this, request.getSignType(), true);
    return result.getShortUrl();
  }

  @Override
  public String shorturl(String longUrl) throws WxPayException {
    return this.shorturl(new WxPayShorturlRequest(longUrl));
  }

  @Override
  public String authcode2Openid(WxPayAuthcode2OpenidRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/tools/authcodetoopenid";
    String responseContent = this.post(url, request.toXML(), false);
    WxPayAuthcode2OpenidResult result = BaseWxPayResult.fromXML(responseContent, WxPayAuthcode2OpenidResult.class);
    result.checkResult(this, request.getSignType(), true);
    return result.getOpenid();
  }

  @Override
  public String authcode2Openid(String authCode) throws WxPayException {
    return this.authcode2Openid(new WxPayAuthcode2OpenidRequest(authCode));
  }

  @Override
  public String getSandboxSignKey() throws WxPayException {
    WxPayDefaultRequest request = new WxPayDefaultRequest();
    request.checkAndSign(this.getConfig());

    String url = "https://api.mch.weixin.qq.com/xdc/apiv2getsignkey/sign/getsignkey";
    String responseContent = this.post(url, request.toXML(), false);
    WxPaySandboxSignKeyResult result = BaseWxPayResult.fromXML(responseContent, WxPaySandboxSignKeyResult.class);
    result.checkResult(this, request.getSignType(), true);
    return result.getSandboxSignKey();
  }

  @Override
  public WxPayCouponSendResult sendCoupon(WxPayCouponSendRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/mmpaymkttransfers/send_coupon";
    String responseContent = this.post(url, request.toXML(), true);
    WxPayCouponSendResult result = BaseWxPayResult.fromXML(responseContent, WxPayCouponSendResult.class);
    result.checkResult(this, request.getSignType(), true);
    return result;
  }

  @Override
  public WxPayCouponStockQueryResult queryCouponStock(WxPayCouponStockQueryRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/mmpaymkttransfers/query_coupon_stock";
    String responseContent = this.post(url, request.toXML(), false);
    WxPayCouponStockQueryResult result = BaseWxPayResult.fromXML(responseContent, WxPayCouponStockQueryResult.class);
    result.checkResult(this, request.getSignType(), true);
    return result;
  }

  @Override
  public WxPayCouponInfoQueryResult queryCouponInfo(WxPayCouponInfoQueryRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/mmpaymkttransfers/querycouponsinfo";
    String responseContent = this.post(url, request.toXML(), false);
    WxPayCouponInfoQueryResult result = BaseWxPayResult.fromXML(responseContent, WxPayCouponInfoQueryResult.class);
    result.checkResult(this, request.getSignType(), true);
    return result;
  }

  @Override
  public WxPayApiData getWxApiData() {
    try {
      return wxApiData.get();
    } finally {
      //???????????????????????????????????????????????????????????????????????????????????????get????????????????????????????????????
      // ??????????????????????????????????????????????????????????????????????????????????????????????????????
      wxApiData.remove();
    }
  }

  @Override
  public String queryComment(Date beginDate, Date endDate, Integer offset, Integer limit) throws WxPayException {
    WxPayQueryCommentRequest request = new WxPayQueryCommentRequest();
    request.setBeginTime(QUERY_COMMENT_DATE_FORMAT.format(beginDate));
    request.setEndTime(QUERY_COMMENT_DATE_FORMAT.format(endDate));
    request.setOffset(offset);
    request.setLimit(limit);

    return this.queryComment(request);
  }

  @Override
  public String queryComment(WxPayQueryCommentRequest request) throws WxPayException {
    request.setSignType(SignType.HMAC_SHA256);// ??????????????????????????????HMAC-SHA256???????????????HMAC-SHA256
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/billcommentsp/batchquerycomment";
    String responseContent = this.post(url, request.toXML(), true);
    if (responseContent.startsWith("<")) {
      throw WxPayException.from(BaseWxPayResult.fromXML(responseContent, WxPayCommonResult.class));
    }

    return responseContent;
  }

  @Override
  public WxPayFaceAuthInfoResult getWxPayFaceAuthInfo(WxPayFaceAuthInfoRequest request) throws WxPayException {
    if (StringUtils.isEmpty(request.getSignType())) {
      request.setSignType(WxPayConstants.SignType.MD5);
    }

    request.checkAndSign(this.getConfig());
    String url = "https://payapp.weixin.qq.com/face/get_wxpayface_authinfo";
    String responseContent = this.post(url, request.toXML(), false);
    WxPayFaceAuthInfoResult result = BaseWxPayResult.fromXML(responseContent, WxPayFaceAuthInfoResult.class);
    result.checkResult(this, request.getSignType(), true);
    return result;
  }

  @Override
  public WxPayFacepayResult facepay(WxPayFacepayRequest request) throws WxPayException {
    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/pay/facepay";
    String responseContent = this.post(url, request.toXML(), false);
    WxPayFacepayResult result = BaseWxPayResult.fromXML(responseContent, WxPayFacepayResult.class);
    result.checkResult(this, request.getSignType(), true);
    return result;
  }

  @Override
  public WxPayQueryExchangeRateResult queryExchangeRate(String feeType, String date) throws WxPayException {
    WxPayQueryExchangeRateRequest request = new WxPayQueryExchangeRateRequest();
    request.setFeeType(feeType);
    request.setDate(date);

    request.checkAndSign(this.getConfig());

    String url = this.getPayBaseUrl() + "/pay/queryexchagerate";
    String responseContent = this.post(url, request.toXML(), false);
    WxPayQueryExchangeRateResult result = BaseWxPayResult.fromXML(responseContent, WxPayQueryExchangeRateResult.class);
    result.checkResult(this, request.getSignType(), true);
    return result;
  }

  @Override
  public ComplaintNotifyResult parseComplaintNotifyResult(String notifyData, SignatureHeader header) throws WxPayException {
    if (Objects.nonNull(header) && !this.verifyNotifySign(header, notifyData)) {
      throw new WxPayException("???????????????????????????????????????");
    }
    OriginNotifyResponse response = GSON.fromJson(notifyData, OriginNotifyResponse.class);
    OriginNotifyResponse.Resource resource = response.getResource();
    String cipherText = resource.getCiphertext();
    String associatedData = resource.getAssociatedData();
    String nonce = resource.getNonce();
    String apiV3Key = this.getConfig().getApiV3Key();
    try {
      String result = AesUtils.decryptToString(associatedData, nonce, cipherText, apiV3Key);
      ComplaintNotifyResult.DecryptNotifyResult decryptNotifyResult = GSON.fromJson(result, ComplaintNotifyResult.DecryptNotifyResult.class);
      ComplaintNotifyResult notifyResult = new ComplaintNotifyResult();
      notifyResult.setRawData(response);
      notifyResult.setResult(decryptNotifyResult);
      return notifyResult;
    } catch (GeneralSecurityException | IOException e) {
      throw new WxPayException("?????????????????????", e);
    }
  }

  @Override
  public ComplaintService getComplaintsService() {
    return complaintsService;
  }

  @Override
  public BankService getBankService() {
    return bankService;
  }

  @Override
  public TransferService getTransferService() {
    return transferService;
  }
}
