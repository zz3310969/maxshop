package cn.lili.modules.payment.kit.plugin.hupijiao;

import cn.hutool.crypto.SecureUtil;
import cn.lili.modules.payment.kit.core.kit.HttpKit;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.*;

public class HuPiJiaoApi {


    /**
     * @param product_type
     * @return
     */
    public static String pay(String appid, String appsecret, String product_type, BigDecimal price1, String notify_url) throws Exception {

        String trade_order_id = UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> sortParams = new HashMap<>();
        sortParams.put("version", "1.1");
//			sortParams.put("redirect", "Y");
        sortParams.put("appid", appid);
        sortParams.put("trade_order_id", trade_order_id);
//			sortParams.put("payment", "wechat");
        sortParams.put("total_fee", price1);
        sortParams.put("title", product_type + "_账号");
        sortParams.put("time", getSecondTimestamp(new Date()));
        //sortParams.put("callback_url", "https://www.xunhupay.com");

        //必填。用户支付成功后，我们服务器会主动发送一个post消息到这个网址(注意：当前接口内，SESSION内容无效)
        sortParams.put("notify_url", notify_url);

        //可选。用户支付成功后，我们会让用户浏览器自动跳转到这个网址
        //sortParams.put("return_url", "https://www.xunhupay.com");

        sortParams.put("nonce_str", getRandomNumber(9));
//			sortParams.put("modal", "full");

        sortParams.put("hash", createSign(sortParams, appsecret));

        System.out.println("开始调三方接口");


        String response = HttpKit.getDelegate().post("https://api.xunhupay.com/payment/do.html", JSON.toJSONString(sortParams));
        System.out.println("调三方接口结束");

        JSONObject jsonObject = JSONObject.parseObject(response);

        if (jsonObject != null) {
            Integer errorcode = jsonObject.getInteger("errcode");
            String errmsg = jsonObject.getString("errmsg");
            if (errorcode == 0 && StringUtils.equals(errmsg, "success!")) {
                System.out.println("返回跳转连接成功");
                System.out.println(jsonObject.get("url_qrcode")); // 可用的收款二维码
                return jsonObject.getString("url_qrcode");
            }
        }

        throw new RuntimeException("支付接口失败");

    }

    /**
     * 生成密钥
     *
     * @param params
     * @param privateKey
     * @return
     */
    public static String createSign(Map<String, Object> params, String privateKey) {

        // 使用HashMap，并使用Arrays.sort排序
        String[] sortedKeys = params.keySet().toArray(new String[]{});
        Arrays.sort(sortedKeys);// 排序请求参数
        StringBuilder builder = new StringBuilder();
        for (String key : sortedKeys) {
            builder.append(key).append("=").append(params.get(key)).append("&");
        }
        String result = builder.deleteCharAt(builder.length() - 1).toString();

        /**
         * 拼接上appsecret
         */
        String stringSignTemp = result + privateKey;

        String signValue = SecureUtil.md5(stringSignTemp);
        return signValue;
    }


    /**
     * 获取精确到秒的时间戳   原理 获取毫秒时间戳，因为 1秒 = 100毫秒 去除后三位 就是秒的时间戳
     *
     * @return
     */
    public static int getSecondTimestamp(Date date) {
        if (null == date) {
            return 0;
        }
        String timestamp = String.valueOf(date.getTime());
        int length = timestamp.length();
        if (length > 3) {
            return Integer.valueOf(timestamp.substring(0, length - 3));
        } else {
            return 0;
        }
    }

    /**
     * 生成一个随机数字
     *
     * @param length 长度自定义
     * @return
     */
    public static String getRandomNumber(int length) {
        String str = "0123456789";
        Random random = new Random();
        StringBuffer sb = new StringBuffer();

        for (int i = 0; i < length; ++i) {
            int number = random.nextInt(str.length());
            sb.append(str.charAt(number));
        }

        return sb.toString();
    }


}
