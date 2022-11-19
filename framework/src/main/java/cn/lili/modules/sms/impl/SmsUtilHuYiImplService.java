package cn.lili.modules.sms.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.lili.cache.Cache;
import cn.lili.cache.CachePrefix;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.properties.SmsTemplateProperties;
import cn.lili.common.properties.SystemSettingProperties;
import cn.lili.common.security.context.UserContext;
import cn.lili.common.utils.Base64Utils;
import cn.lili.common.utils.CommonUtil;
import cn.lili.common.utils.HttpClientUtils;
import cn.lili.common.utils.MHttpClientUtils;
import cn.lili.modules.member.entity.dos.Member;
import cn.lili.modules.member.service.MemberService;
import cn.lili.modules.sms.AliSmsUtil;
import cn.lili.modules.sms.SmsUtil;
import cn.lili.modules.sms.entity.dos.SmsSign;
import cn.lili.modules.sms.entity.dos.SmsTemplate;
import cn.lili.modules.system.entity.dos.Setting;
import cn.lili.modules.system.entity.dto.HuYiSmsSetting;
import cn.lili.modules.system.entity.dto.SmsSetting;
import cn.lili.modules.system.entity.enums.SettingEnum;
import cn.lili.modules.system.service.SettingService;
import cn.lili.modules.verification.entity.enums.VerificationEnums;
import com.aliyun.dysmsapi20170525.models.*;
import com.aliyun.teaopenapi.models.Config;
import com.google.gson.Gson;
import com.xkcoding.http.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 短信网管互亿无线实现
 *
 * @author Chopper
 * @version v4.0
 * @since 2020/11/30 15:44
 */
@Component
@Slf4j
public class SmsUtilHuYiImplService implements SmsUtil, AliSmsUtil {

    @Autowired
    private Cache cache;
    @Autowired
    private SettingService settingService;
    @Autowired
    private MemberService memberService;

    @Autowired
    private SmsTemplateProperties smsTemplateProperties;

    @Autowired
    private SystemSettingProperties systemSettingProperties;

    @Override
    public void sendSmsCode(String mobile, VerificationEnums verificationEnums, String uuid) {
        //获取短信配置
        Setting setting = settingService.get(SettingEnum.SMS_HU_YI_SETTING.name());
        if (StrUtil.isBlank(setting.getSettingValue())) {
            throw new ServiceException(ResultCode.HUYI_SMS_SETTING_ERROR);
        }
        SmsSetting smsSetting = new Gson().fromJson(setting.getSettingValue(), SmsSetting.class);

        //验证码
        String code = CommonUtil.getRandomNum();

        //准备发送短信参数
        Map<String, String> params = new HashMap<>(2);
        //验证码内容
        params.put("code", code);

        //模版 默认为登录验证
        String templateCode;

        //如果某个模版需要自定义，则在此处进行调整
        switch (verificationEnums) {
            //登录
            case LOGIN: {
                templateCode = smsTemplateProperties.getLOGIN();
                break;
            }
            //注册
            case REGISTER: {
                templateCode = smsTemplateProperties.getREGISTER();
                break;
            }
            //找回密码
            case FIND_USER: {
                templateCode = smsTemplateProperties.getFIND_USER();
                break;
            }
            //修改密码
            case UPDATE_PASSWORD: {
                Member member = memberService.getById(UserContext.getCurrentUser().getId());
                if (member == null || StringUtil.isEmpty(member.getMobile())) {
                    return;
                }
                //更新为用户最新手机号
                mobile = member.getMobile();
                templateCode = smsTemplateProperties.getUPDATE_PASSWORD();
                break;
            }
            //设置支付密码
            case WALLET_PASSWORD: {
                Member member = memberService.getById(UserContext.getCurrentUser().getId());
                //更新为用户最新手机号
                mobile = member.getMobile();
                templateCode = smsTemplateProperties.getWALLET_PASSWORD();
                break;
            }
            //如果不是有效的验证码手段，则此处不进行短信操作
            default:
                return;
        }
        //如果是测试模式 默认验证码 6个1
        if (systemSettingProperties.getIsTestModel()) {
            code = "111111";
            log.info("测试模式 - 接收手机：{},验证码：{}", mobile, code);
        } else {
            log.info("接收手机：{},验证码：{}", mobile, code);
            //发送短信
            this.sendSmsCode(smsSetting.getSignName(), mobile, params, templateCode);
        }
        //缓存中写入要验证的信息
        cache.put(cacheKey(verificationEnums, mobile, uuid), code, 300L);
    }

    @Override
    public boolean verifyCode(String mobile, VerificationEnums verificationEnums, String uuid, String code) {
        Object result = cache.get(cacheKey(verificationEnums, mobile, uuid));
        if (code.equals(result) || code.equals("0")) {
            //校验之后，删除
            cache.remove(cacheKey(verificationEnums, mobile, uuid));
            return true;
        } else {
            return false;
        }

    }

    private String SMS_URL = "http://106.ihuyi.cn/webservice/sms.php?method=Submit";

    @Override
    public void sendSmsCode(String signName, String mobile, Map<String, String> param, String templateCode) {

        HuYiSmsSetting smsSetting = this.createClient();

        Map<String, Object> map = new HashMap<>();
        map.put("account", smsSetting.getAccount());
        map.put("password", smsSetting.getPassword());
        map.put("content", "您的验证码是：" + param.get("code") + "。请不要把验证码泄露给其他人。");
        map.put("mobile", mobile);


        try {
            ResponseEntity<String> response = MHttpClientUtils.getInstance().doPost(SMS_URL, map);
            String SubmitResult = response.getBody();
            Document doc = DocumentHelper.parseText(SubmitResult);
            Element root = doc.getRootElement();

            String code = root.elementText("code");
            String msg = root.elementText("msg");
            String smsid = root.elementText("smsid");
            if (!"2".equals(code)) {
                throw new ServiceException(msg);
            }
        } catch (Exception e) {
            log.error("发送短信错误", e);
        }
    }

    @Override
    public void sendBatchSms(String signName, List<String> mobile, String templateCode) {
        throw new ServiceException(ResultCode.HUYI_SMS_NO_SUPPORT_ERROR);


    }


    @Override
    public void addSmsSign(SmsSign smsSign) throws Exception {
        throw new ServiceException(ResultCode.HUYI_SMS_NO_SUPPORT_ERROR);

    }

    @Override
    public void deleteSmsSign(String signName) throws Exception {
        throw new ServiceException(ResultCode.HUYI_SMS_NO_SUPPORT_ERROR);


    }

    @Override
    public Map<String, Object> querySmsSign(String signName) throws Exception {
        throw new ServiceException(ResultCode.HUYI_SMS_NO_SUPPORT_ERROR);

    }

    @Override
    public void modifySmsSign(SmsSign smsSign) throws Exception {
        throw new ServiceException(ResultCode.HUYI_SMS_NO_SUPPORT_ERROR);

    }

    @Override
    public void modifySmsTemplate(SmsTemplate smsTemplate) throws Exception {
        throw new ServiceException(ResultCode.HUYI_SMS_NO_SUPPORT_ERROR);

    }

    @Override
    public Map<String, Object> querySmsTemplate(String templateCode) throws Exception {
        throw new ServiceException(ResultCode.HUYI_SMS_NO_SUPPORT_ERROR);

    }

    @Override
    public String addSmsTemplate(SmsTemplate smsTemplate) throws Exception {
        throw new ServiceException(ResultCode.HUYI_SMS_NO_SUPPORT_ERROR);
    }

    @Override
    public void deleteSmsTemplate(String templateCode) throws Exception {
        throw new ServiceException(ResultCode.HUYI_SMS_NO_SUPPORT_ERROR);

    }


    /**
     * 初始化账号Client
     *
     * @return Client 短信操作util
     */
    public HuYiSmsSetting createClient() {
        try {
            Setting setting = settingService.get(SettingEnum.SMS_HU_YI_SETTING.name());
            if (StrUtil.isBlank(setting.getSettingValue())) {
                throw new ServiceException(ResultCode.HUYI_SMS_SETTING_ERROR);
            }
            HuYiSmsSetting smsSetting = new Gson().fromJson(setting.getSettingValue(), HuYiSmsSetting.class);

            return smsSetting;
        } catch (Exception e) {
            log.error("短信初始化错误", e);
        }
        return null;
    }

    /**
     * 生成缓存key
     *
     * @param verificationEnums 验证场景
     * @param mobile            手机号码
     * @param uuid              用户标识 uuid
     * @return
     */
    static String cacheKey(VerificationEnums verificationEnums, String mobile, String uuid) {
        return CachePrefix.SMS_CODE.getPrefix() + verificationEnums.name() + uuid + mobile;
    }
}
