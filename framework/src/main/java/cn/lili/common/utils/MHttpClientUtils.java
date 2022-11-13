package cn.lili.common.utils;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Created by hzliuxin1 on 2016/8/26.
 */
@Slf4j
public class MHttpClientUtils {

    private static HttpClient httpClient;
    private int socketTimeout = 2000;
    private int connectTimeout = 2000;
    private int connectionRequestTimeout = 2000;
    private static volatile MHttpClientUtils INSTANCE = null;

    private MHttpClientUtils() {
    }

    public static MHttpClientUtils getInstance() {
        if (INSTANCE == null) {
            synchronized (MHttpClientUtils.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MHttpClientUtils();
                }
            }
        }
        return INSTANCE;
    }

    static {
        ConnectionSocketFactory plainsf = PlainConnectionSocketFactory
                .getSocketFactory();
        LayeredConnectionSocketFactory sslsf = SSLConnectionSocketFactory
                .getSocketFactory();
        Registry<ConnectionSocketFactory> registry = RegistryBuilder
                .<ConnectionSocketFactory>create().register("http", plainsf)
                .register("https", sslsf).build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(
                registry);
        cm.setMaxTotal(5000);
        cm.setDefaultMaxPerRoute(200);
        HttpHost toutiao = new HttpHost("developer.toutiao.com", 443);
        cm.setMaxPerRoute(new HttpRoute(toutiao), 300);
        httpClient = HttpClients.custom().setConnectionManager(cm).build();
    }

    public ResponseEntity<String> doGet(String url) throws IOException {
        HttpGet httpGet = new HttpGet(url);
        httpGet.setConfig(RequestConfig.custom().setSocketTimeout(socketTimeout).setConnectTimeout(connectTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout).build());
        HttpResponse httpResponse = null;
        try {
            httpResponse = httpClient.execute(httpGet);
            return new ResponseEntity(EntityUtils.toString(httpResponse.getEntity()),
                    HttpStatus.valueOf(httpResponse.getStatusLine().getStatusCode()));
        } finally {
            close(httpGet, httpResponse);
        }
    }

    public String doGetString(String url) throws IOException {
        ResponseEntity<String> responseEntity = doGet(url);
        if (responseEntity == null) {
            throw new IOException("http get return null");
        }
        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
            throw new IOException("http get error, code " + responseEntity.getStatusCode().toString());
        }
        return responseEntity.getBody();
    }

    private void close(HttpRequestBase httpRequestBase, HttpResponse httpResponse) {
        try {
            if (httpRequestBase != null) {
                httpRequestBase.releaseConnection();
            }
        } finally {
            if (httpResponse != null) {
                try {
                    try {
                        EntityUtils.consume(httpResponse.getEntity());
                    } finally {
                        if (httpResponse instanceof Closeable) {
                            ((Closeable) httpResponse).close();
                        }
                    }
                } catch (IOException ex) {
                    log.error(ex.getMessage(), ex);
                }
            }
        }

    }

    public ResponseEntity<String> doPost(String url, Map<String, Object> params) throws IOException {
        return doPost(url, null, params);
    }

    public String doPostString(String url, Map<String, Object> params) throws IOException {
        ResponseEntity<String> responseEntity = doPost(url, params);
        if (responseEntity == null) {
            throw new IOException("http post return null");
        }
        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
            throw new IOException("http post error, code " + responseEntity.getStatusCode().toString());
        }
        return responseEntity.getBody();
    }


    public ResponseEntity<String> doPost(String url, Map<String, String> heads, Map<String, Object> params, String defaultCharset)
            throws IOException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setConfig(RequestConfig.custom().setSocketTimeout(socketTimeout).setConnectTimeout(connectTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout).build());
        HttpResponse httpResponse = null;
        try {
            if (heads != null) {
                for (Map.Entry<String, String> head : heads.entrySet()) {
                    httpPost.addHeader(head.getKey(), head.getValue());
                }
            }
            if (params != null) {
                List<NameValuePair> nvps = new ArrayList<>();
                for (Map.Entry<String, Object> param : params.entrySet()) {
                    nvps.add(new BasicNameValuePair(param.getKey(), param.getValue().toString()));
                }
                httpPost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));
            }
            httpResponse = httpClient.execute(httpPost);
            return new ResponseEntity(EntityUtils.toString(httpResponse.getEntity(), defaultCharset),
                    HttpStatus.valueOf(httpResponse.getStatusLine().getStatusCode()));
        } finally {
            close(httpPost, httpResponse);
        }
    }


    public ResponseEntity<String> doPostJson(String url, Map<String, String> heads, Map<String, Object> params, String defaultCharset)
            throws IOException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setConfig(RequestConfig.custom().setSocketTimeout(socketTimeout).setConnectTimeout(connectTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout).build());
        HttpResponse httpResponse = null;
        try {
            if (heads != null) {
                for (Map.Entry<String, String> head : heads.entrySet()) {
                    httpPost.addHeader(head.getKey(), head.getValue());
                }
            }
            if (params != null) {
                httpPost.setEntity(new StringEntity(JSON.toJSONString(params)));
            }
            httpResponse = httpClient.execute(httpPost);
            return new ResponseEntity(EntityUtils.toString(httpResponse.getEntity(), defaultCharset),
                    HttpStatus.valueOf(httpResponse.getStatusLine().getStatusCode()));
        } finally {
            close(httpPost, httpResponse);
        }
    }


    public String doPostBytes(String url, Map<String, String> heads, Map<String, Object> params)
            throws IOException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setConfig(RequestConfig.custom().setSocketTimeout(socketTimeout).setConnectTimeout(connectTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout).build());
        HttpResponse httpResponse = null;
        try {
            if (heads != null) {
                for (Map.Entry<String, String> head : heads.entrySet()) {
                    httpPost.addHeader(head.getKey(), head.getValue());
                }
            }
            if (params != null) {
                httpPost.setEntity(new StringEntity(JSON.toJSONString(params)));
            }
            httpResponse = httpClient.execute(httpPost);

            BufferedInputStream bis = new BufferedInputStream(httpResponse.getEntity().getContent());
            try (InputStream is = httpResponse.getEntity().getContent(); ByteArrayOutputStream baos = new ByteArrayOutputStream();){
                byte[] buffer = new byte[1024];
                int len = -1;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                return Base64.getEncoder().encodeToString(baos.toByteArray());
            }

            /*return new ResponseEntity(EntityUtils.toByteArray(httpResponse.getEntity()),
                    HttpStatus.valueOf(httpResponse.getStatusLine().getStatusCode()));*/
        } finally {
            close(httpPost, httpResponse);
        }
    }

    public ResponseEntity<String> doPostJson(String url, Map<String, String> heads, Map<String, Object> params)
            throws IOException {
        return doPostJson(url, heads, params, null);
    }

    public ResponseEntity<String> doPost(String url, Map<String, String> heads, Map<String, Object> params)
            throws IOException {
        return doPost(url, heads, params, null);
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    public void setConnectionRequestTimeout(int connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }
}
