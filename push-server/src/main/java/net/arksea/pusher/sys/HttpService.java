package net.arksea.pusher.sys;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;

@Component
public class HttpService {
    private static Logger logger = LogManager.getLogger(HttpService.class);
    @Value("${httpclient.connectTimeout}")
    private int connectTimeout;
    @Value("${httpclient.socketTimeout}")
    private int socketTimeout;
    @Value("${httpclient.maxConnTotal}")
    private int maxConnTotal;
    @Value("${httpclient.maxConnPerRoute}")
    private int maxConnPerRoute;
    private volatile CloseableHttpClient httpclient;

    @PostConstruct
    public void init() {
        final RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(connectTimeout)
            .setSocketTimeout(socketTimeout)
            .build();
        this.httpclient = HttpClients.custom()
            .setDefaultRequestConfig(config)
            .setMaxConnTotal(maxConnTotal)
            .setMaxConnPerRoute(maxConnPerRoute)
            .setKeepAliveStrategy(HttpClientHelper.createKeepAliveStrategy(300))
            .build();
    }

    public String get(String url) throws Exception {
        HttpGet httpget = new HttpGet(url);
        CloseableHttpResponse response = httpclient.execute(httpget);
        try {
            HttpEntity entity = response.getEntity();
            int code = response.getStatusLine().getStatusCode();
            if (entity != null) {
                String data = EntityUtils.toString(entity);
                logger.debug("Http Respond(status={}): {}", response.getStatusLine().getStatusCode(), data);
                if (code == 200) {
                    return data;
                } else {
                    throw new RuntimeException("Http request failed, status=" + code + ", url="+url+", body: " + data);
                }
            } else {
                throw new RuntimeException("Http request failed, status=" + code + ", url="+url );
            }
        } catch (Exception ex) {
            throw new RuntimeException("Http request failed" , ex);
        } finally {
            response.close();
        }
    }

    public String post(String url, String body) throws Exception {
        HttpPost httpPost = new HttpPost(url);
        StringEntity se = new StringEntity(body, APPLICATION_JSON);
        httpPost.setEntity(se);
        CloseableHttpResponse response = httpclient.execute(httpPost);
        try {
            HttpEntity entity = response.getEntity();
            int code = response.getStatusLine().getStatusCode();
            if (entity != null) {
                String data = EntityUtils.toString(entity);
                logger.debug("Http Respond(status={}): {}", response.getStatusLine().getStatusCode(), data);
                if (code == 200) {
                    return data;
                } else {
                    throw new RuntimeException("Http request failed, status=" + code + ", url="+url+", body: " + data);
                }
            } else {
                throw new RuntimeException("Http request failed, status=" + code + ", url="+url );
            }
        } catch (Exception ex) {
            throw new RuntimeException("Http request failed" , ex);
        } finally {
            response.close();
        }
    }
}