package cn.lili.modules.payment.kit.plugin.hupijiao;

import cn.hutool.core.net.URLDecoder;
import cn.hutool.core.net.URLEncoder;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.properties.ApiProperties;
import cn.lili.common.utils.CurrencyUtil;
import cn.lili.common.utils.SnowFlake;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.payment.entity.enums.PaymentMethodEnum;
import cn.lili.modules.payment.kit.CashierSupport;
import cn.lili.modules.payment.kit.Payment;
import cn.lili.modules.payment.kit.core.PaymentHttpResponse;
import cn.lili.modules.payment.kit.core.enums.RequestMethodEnums;
import cn.lili.modules.payment.kit.core.kit.HttpKit;
import cn.lili.modules.payment.kit.core.kit.WxPayKit;
import cn.lili.modules.payment.kit.core.utils.DateTimeZoneUtil;
import cn.lili.modules.payment.kit.dto.PayParam;
import cn.lili.modules.payment.kit.dto.PaymentSuccessParams;
import cn.lili.modules.payment.kit.params.dto.CashierParam;
import cn.lili.modules.payment.kit.plugin.wechat.WechatApi;
import cn.lili.modules.payment.kit.plugin.wechat.enums.WechatApiEnum;
import cn.lili.modules.payment.kit.plugin.wechat.enums.WechatDomain;
import cn.lili.modules.payment.kit.plugin.wechat.model.Amount;
import cn.lili.modules.payment.kit.plugin.wechat.model.UnifiedOrderModel;
import cn.lili.modules.payment.service.PaymentService;
import cn.lili.modules.system.entity.dos.Setting;
import cn.lili.modules.system.entity.dto.payment.HuPiJiaoPaymentSetting;
import cn.lili.modules.system.entity.dto.payment.WechatPaymentSetting;
import cn.lili.modules.system.entity.enums.SettingEnum;
import cn.lili.modules.system.service.SettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class HuPiJiaoPlugin implements Payment {

    @Autowired
    private SettingService settingService;
    /**
     * 收银台
     */
    @Autowired
    private CashierSupport cashierSupport;
    /**
     * 支付日志
     */
    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ApiProperties apiProperties;

    private HuPiJiaoPaymentSetting huPiJiaoPaymentSetting() {
        try {
            Setting systemSetting = settingService.get(SettingEnum.HUPIJIAO_PAYMENT.name());
            HuPiJiaoPaymentSetting wechatPaymentSetting = JSONUtil.toBean(systemSetting.getSettingValue(), HuPiJiaoPaymentSetting.class);
            return wechatPaymentSetting;
        } catch (Exception e) {
            log.error("虎皮椒支付暂不支持", e);
            throw new ServiceException(ResultCode.PAY_NOT_SUPPORT);
        }
    }

    @Override
    public ResultMessage<Object> h5pay(HttpServletRequest request, HttpServletResponse response, PayParam payParam) {
        CashierParam cashierParam = cashierSupport.cashierParam(payParam);

        HuPiJiaoPaymentSetting setting = huPiJiaoPaymentSetting();
        String appid = setting.getAppid();
        if (appid == null) {
            throw new ServiceException(ResultCode.HUPIJIAO_PAYMENT_NOT_SETTING);
        }

        //支付金额
        Double fen = cashierParam.getPrice();

        String notifyUrl = notifyUrl(apiProperties.getBuyer(), PaymentMethodEnum.HUPIJIAO);

        String code = null;
        try {
            code = HuPiJiaoApi.pay(appid, setting.getAppsecret(), payParam.getSn(),"", new BigDecimal(fen), notifyUrl);
            return ResultUtil.data(JSONUtil.toJsonStr(code));

        } catch (Exception e) {
            log.error("虎皮椒H5支付错误", e);
            throw new ServiceException(ResultCode.PAY_ERROR);
        }

    }


    @Override
    public ResultMessage<Object> nativePay(HttpServletRequest request, PayParam payParam) {
        CashierParam cashierParam = cashierSupport.cashierParam(payParam);

        HuPiJiaoPaymentSetting setting = huPiJiaoPaymentSetting();
        String appid = setting.getAppid();
        if (appid == null) {
            throw new ServiceException(ResultCode.HUPIJIAO_PAYMENT_NOT_SETTING);
        }

        //支付金额
        Double fen = cashierParam.getPrice();

        String notifyUrl = notifyUrl("http://35.188.205.168:8888", PaymentMethodEnum.HUPIJIAO);

        String code = null;
        try {
            code = HuPiJiaoApi.pay(appid, setting.getAppsecret(), payParam.getSn(),"", new BigDecimal(fen), notifyUrl);
            return ResultUtil.data(JSONUtil.toJsonStr(code));
        } catch (Exception e) {
            log.error("虎皮椒二维码支付错误", e);
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
    }

    @Override
    public void callBack(HttpServletRequest request) {
        try {
            verifyNotify(request);
        } catch (Exception e) {
            log.error("支付异常", e);
        }
    }


    /**
     * 验证结果，执行支付回调
     *
     * @param request
     * @throws Exception
     */
    private void verifyNotify(HttpServletRequest request) throws Exception {

        String trade_order_id = request.getParameter("trade_order_id");

        String result = HttpKit.readData(request);
        log.info("微信支付通知密文 {}", result);

        HuPiJiaoPaymentSetting setting = huPiJiaoPaymentSetting();


        JSONObject jsonObject = JSONUtil.parseObj(result);

        String payParamStr = jsonObject.getStr("attach");
        String payParamJson = URLDecoder.decode(payParamStr, StandardCharsets.UTF_8);
        PayParam payParam = JSONUtil.toBean(payParamJson, PayParam.class);


        String tradeNo = jsonObject.getStr("open_order_id");
        Double totalAmount = CurrencyUtil.reversalFen(jsonObject.getDouble("total_fee"));

        PaymentSuccessParams paymentSuccessParams = new PaymentSuccessParams(
                PaymentMethodEnum.HUPIJIAO.name(),
                tradeNo,
                totalAmount,
                payParam
        );

        paymentService.success(paymentSuccessParams);
        //log.info("微信支付回调：支付成功{}", plainText);
    }
}
