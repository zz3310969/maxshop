package cn.lili.modules.system.entity.dto.payment;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 虎皮椒支付设置
 *
 * @author Chopper
 * @since 2020-12-02 10:09
 */
@Data
@Accessors(chain = true)
public class HuPiJiaoPaymentSetting {

    /**
     * 应用id
     */
    private String appid;

    /**
     * 私钥
     */
    private String appsecret;


}
