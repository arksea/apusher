package net.arksea.pusher.huawei;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.arksea.pusher.*;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2018/10/26.
 */
public class PushClient implements IPushClient<String> {
    private final static Logger logger = LogManager.getLogger(PushClient.class);
    private final static ObjectMapper objectMapper = new ObjectMapper();
    private String accessToken;
    private long accessTokenExpiresTime;
    private final String appId;
    private final String appKey;
    private final static String TOKEN_URL = "https://login.cloud.huawei.com/oauth2/v2/token";
    private final String PUSH_URL;
    private final ZoneOffset localZone = ZoneOffset.of("+8");
    private final RequestConfig requestConfig;
    private final long BACKOFF_MIN = 1000;
    private final long BACKOFF_MAX = 8000;
    private final ZoneOffset zone = ZoneOffset.of("+8");
    private long backoff = BACKOFF_MIN;
    public PushClient(String appId, String appKey) throws UnsupportedEncodingException {
        this.appId = appId;
        this.appKey = appKey;
        String nspCtx = "{\"ver\":\"1\", \"appId\":\"" + appId + "\"}";
        this.PUSH_URL = "https://api.push.hicloud.com/pushsend.do?nsp_ctx=" + URLEncoder.encode(nspCtx, "UTF-8");
        requestConfig = RequestConfig.custom()
            .setSocketTimeout(3000)
            .setConnectTimeout(3000)
            .build();
    }

    @Override
    public void connect(IConnectionStatusListener listener) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            updateAccessToken(client, listener);
        }
    }

    private void updateAccessToken(CloseableHttpClient httpclient, IConnectionStatusListener listener) {
        try {
            HttpPost post = new HttpPost(TOKEN_URL);
            post.setConfig(requestConfig);
            post.addHeader("Content-Type", "application/x-www-form-urlencoded");
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("grant_type", "client_credentials"));
            params.add(new BasicNameValuePair("client_secret", appKey));
            params.add(new BasicNameValuePair("client_id", appId));
            post.setEntity(new UrlEncodedFormEntity(params));
            CloseableHttpResponse response = httpclient.execute(post);
            int code = response.getStatusLine().getStatusCode();
            String body = readBody(response);
            if (code == 200) {
                Map map = objectMapper.readValue(body, Map.class);
                this.accessToken = (String) map.get("access_token");
                int expiresIn = (Integer) map.get("expires_in");
                accessTokenExpiresTime = System.currentTimeMillis() + expiresIn * 1000;
                listener.connected(this.accessToken);
                logger.info("Update access token succeed, expires time: {}",
                    LocalDateTime.ofEpochSecond(accessTokenExpiresTime/1000,0,zone));
            } else {
                logger.warn("Get huawei access token failed: code={}, result={}", code, body);
                close(accessToken);
                listener.reconnect();
            }
        } catch (Exception ex) {
            logger.warn("Get huawei access token failed", ex);
            close(accessToken);
            listener.reconnect();
        }
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

    private boolean validToken(String token) {
        int len = token.length();
        return len==32 || len==130;
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
            post.addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            LocalDateTime dt = LocalDateTime.ofEpochSecond(event.expiredTime/1000, 0, localZone);
            String expireTime = dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            StringBuilder tokensBuff = new StringBuilder();
            tokensBuff.append("[");
            int n = 0;
            for (String t : event.tokens) {
                if (validToken(t)) {
                    ++n;
                    tokensBuff.append("\"");
                    tokensBuff.append(t);
                    tokensBuff.append("\",");
                } else {
                    statusListener.handleInvalidToken(t);
                }
            }
            if (n == 0) {
                statusListener.onPushSucceed(event, event.tokens.length);
                logger.debug("华为推送成功, tokens.length={}", event.tokens.length);
                return;
            }
            tokensBuff.setCharAt(tokensBuff.length() - 1, ']');
            List<NameValuePair> params = new ArrayList<>();
            NameValuePair p1 = new BasicNameValuePair("access_token", this.accessToken);
            params.add(p1);
            NameValuePair p2 = new BasicNameValuePair("nsp_svc", "openpush.message.api.send");
            params.add(p2);
            NameValuePair p3 = new BasicNameValuePair("nsp_ts", String.valueOf(System.currentTimeMillis() / 1000));
            params.add(p3);
            NameValuePair p4 = new BasicNameValuePair("device_token_list", tokensBuff.toString());
            params.add(p4);
            NameValuePair p5 = new BasicNameValuePair("payload", event.payload);
            params.add(p5);
            NameValuePair p6 = new BasicNameValuePair("expire_time", expireTime);
            params.add(p6);
            HttpEntity entity = new UrlEncodedFormEntity(params, "UTF-8");
            post.setEntity(entity);
            CloseableHttpResponse response = httpclient.execute(post);
            int code = response.getStatusLine().getStatusCode();
            String nspStatus = null;
            Header h = response.getFirstHeader("NSP_STATUS");
            if (h != null) {
                nspStatus = h.getValue();
            }
            if (code == 503) { //系统级失败：流控
                logger.warn("华为推送流控错误, client={}, backoff={}", this, backoff);
                statusListener.onRateLimit(event);
            } else if (code != 200) { //系统级失败：通讯错误
                logger.warn("华为推送错误, statusCode={}", code);
                statusListener.onPushFailed(event, event.tokens.length);
                connListener.onFailed();
            } else if (nspStatus != null && !"0".equals(nspStatus)) { // 系统级失败
                logger.warn("华为推送错误, nspStatus={}, token expires time:{} ", nspStatus
                    , LocalDateTime.ofEpochSecond(accessTokenExpiresTime/1000,0,zone));
                statusListener.onPushFailed(event, event.tokens.length);
                connListener.onFailed();
            } else {
                String body = readBody(response);
                Map map = objectMapper.readValue(body, Map.class);
                String scode = (String) map.get("code");
                if ("80000000".equals(scode)) { //成功
                    logger.debug("华为推送成功, body={}, tokens.length={}", body, event.tokens.length);
                    statusListener.onPushSucceed(event, event.tokens.length);
                } else { //应用级失败
                    String msg = (String) map.get("msg");
                    Map msgMap = objectMapper.readValue(msg, Map.class);
                    handleMessage(msgMap, body, event, statusListener);
                }
            }
            long time = System.currentTimeMillis() - start;
            if (time > 2000) {
                logger.info("huawei push succeed, use time={}ms, client={}", time, this);
            } else {
                logger.debug("huawei push succeed, use time={}ms, client={}", time, this);
            }
            if (code == 503) {
                Thread.sleep(backoff); //遇到流控失败时进行退避延时
                backoff = Math.min(backoff * 2, BACKOFF_MAX);
            } else if (code == 200 && backoff > BACKOFF_MIN){
                backoff = Math.max(BACKOFF_MIN, backoff - 1000);
            }
        } catch (Exception ex) {
            long time = System.currentTimeMillis() - start;
            logger.warn("huawei push failed, time={}ms, reason: {}, client={}", time, ex.getMessage(), this);
            statusListener.onPushFailed(event, event.tokens.length);
            connListener.onFailed();
        }
    }

    private void handleMessage(Map map,  String body, PushEvent event, IPushStatusListener statusListener) {
        if (map == null) {
            statusListener.onPushFailed(event, event.tokens.length);
            logger.warn("华为推送错误, body={}, tokens.length={}", body, event.tokens.length);
            return;
        }
        Object obj = map.get("illegal_tokens");
        if (obj != null) {
            if (obj instanceof List) {
                List<String> tokens = (List<String>) obj;
                Integer success = (Integer) map.get("success");
                if (success == null || success == 0) {
                    logger.debug("华为推送成功, body={}, tokens.length={}, illegal_tokens={}",
                        body, event.tokens.length, tokens.size());
                } else {
                    statusListener.onPushSucceed(event, success);
                    logger.debug("华为推送成功, body={}, tokens.length={}, illegal_tokens={}",
                        body, event.tokens.length, tokens.size());
                }
                logger.info("标记{}个无效Token： {}", tokens.size(), body);
                for (String token : tokens) {
                    statusListener.handleInvalidToken(token);
                }
            } else {
                statusListener.onPushFailed(event, event.tokens.length);
                logger.warn("华为推送错误, body={}, tokens.length={}, obj.class={}", body, event.tokens.length, obj.getClass());
            }
        } else {
            statusListener.onPushFailed(event, event.tokens.length);
            logger.warn("华为推送错误, body={}, tokens.length={}", body, event.tokens.length);
        }
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
