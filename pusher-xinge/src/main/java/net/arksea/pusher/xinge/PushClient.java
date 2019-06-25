package net.arksea.pusher.xinge;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.arksea.pusher.IConnectionStatusListener;
import net.arksea.pusher.IPushClient;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.PushEvent;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2019/09/55.
 */
public class PushClient implements IPushClient<String> {
    private final static Logger logger = LogManager.getLogger(PushClient.class);
    private final static ObjectMapper objectMapper = new ObjectMapper();
    private long accessTokenExpiresTime;
    private final String postmanToken;
    private final String authorization;
    private final String PUSH_URL;
    private final RequestConfig requestConfig;
    private final long BACKOFF_MIN = 1000;
    private final long BACKOFF_MAX = 8000;
    private long backoff = BACKOFF_MIN;
    public PushClient(String appId, String appKey, String postmanToken) throws UnsupportedEncodingException {
        this.postmanToken = postmanToken;
        this.authorization = "Basic " + Base64.encodeBase64String((appId+":"+appKey).getBytes());
        this.PUSH_URL = "https://openapi.xg.qq.com/v3/push/app";
        requestConfig = RequestConfig.custom()
            .setSocketTimeout(3000)
            .setConnectTimeout(3000)
            .build();
    }

    @Override
    public void connect(IConnectionStatusListener listener) throws Exception {
        listener.connected("");
    }

    private String readBody(HttpResponse response) throws IOException {
        final StringBuilder sb = new StringBuilder();
        InputStreamReader reader;
        reader = new InputStreamReader(response.getEntity().getContent(), "UTF-8");
        char[] cbuf = new char[128];
        int len;
        while ((len = reader.read(cbuf)) > -1) {
            sb.append(cbuf, 0, len);
        }
        return sb.toString();
    }

    @Override
    public void push(String session, PushEvent event, IConnectionStatusListener connListener, IPushStatusListener statusListener) {
        long start = System.currentTimeMillis();
        if (event.testEvent) {
            statusListener.onPushSucceed(event, event.tokens.length);
            return;
        }
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(PUSH_URL);
            post.setConfig(requestConfig);
            post.addHeader("Content-Type", "application/json;charset=UTF-8");
            post.addHeader("Authorization", authorization);
            post.addHeader("Cache-Control", "no-cache");
            post.addHeader("Postman-Token", postmanToken);
            Map msgMap = objectMapper.readValue(event.payload, Map.class);
            msgMap.put("audience_type", event.tokens.length > 1 ? "token_list" : "token");
            msgMap.put("token_list", event.tokens);
            String msg = objectMapper.writeValueAsString(msgMap);
            HttpEntity entity = new StringEntity(msg, "UTF-8");
            post.setEntity(entity);
            CloseableHttpResponse response = httpclient.execute(post);
            int code = response.getStatusLine().getStatusCode();
            int retcode = 0;
            if (code == 200) {
                String body = readBody(response);
                Map map = objectMapper.readValue(body, Map.class);
                retcode = (int) map.get("ret_code");
                if (retcode == 0) { //成功
                    logger.debug("信鸽推送成功, body={}, tokens.length={}", body, event.tokens.length);
                    statusListener.onPushSucceed(event, event.tokens.length);
                } else { //应用级失败
                    handleMessage(map, body, event, statusListener);
                }
            } else { //系统级失败：通讯错误
                logger.warn("信鸽推送，Http请求失败，statusCode={}", code);
                statusListener.onPushFailed(event, event.tokens.length);
                connListener.onFailed();
            }
            //记录费时推送用于观察
            long time = System.currentTimeMillis() - start;
            if (time > 1000) {
                logger.info("XinGe push succeed, use time={}ms, client={}", time, this);
            } else {
                logger.debug("XinGe push succeed, use time={}ms, client={}", time, this);
            }
            //遇到因繁忙失败时进行退避延时
            if (retcode == 10100 || retcode == 10101) {
                Thread.sleep(backoff);
                backoff = Math.min(backoff * 2, BACKOFF_MAX);
            } else if (code == 200 && backoff > BACKOFF_MIN){
                backoff = Math.max(BACKOFF_MIN, backoff - 1000);
            }
        } catch (Exception ex) {
            long time = System.currentTimeMillis() - start;
            logger.warn("XinGe push failed, time={}ms, reason: {}, client={}", time, ex.getMessage(), this);
            statusListener.onPushFailed(event, event.tokens.length);
            connListener.onFailed();
        }
    }

    private void handleMessage(Map map,  String body, PushEvent event, IPushStatusListener statusListener) {
        statusListener.onPushFailed(event, event.tokens.length);
        logger.warn("信鸽推送错误, body={}, tokens.length={}", body, event.tokens.length);
    }

    @Override
    public void ping(String session, IConnectionStatusListener listener) throws Exception {
        logger.trace("PushClient.ping: {}", session);
        long now = System.currentTimeMillis();
        if (this.accessTokenExpiresTime - now < 600000) {
            connect(listener);
        }
    }

    @Override
    public boolean isAvailable(String session) {
        long now = System.currentTimeMillis();
        logger.trace("PushClient.isAvailable(), time={}", this.accessTokenExpiresTime - now);
        return this.accessTokenExpiresTime - now > 300000;
    }

    @Override
    public void close(String session) {
        logger.trace("PushClient.close()");
        this.accessTokenExpiresTime = 0;
    }
}
