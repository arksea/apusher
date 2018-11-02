package net.arksea.pusher.huawei;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.arksea.pusher.*;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
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
    CloseableHttpClient httpclient;
    private String accessToken;
    private long accessTokenExpiresTime;
    final private String appId;
    final private String appKey;
    final private static String TOKEN_URL = "https://login.cloud.huawei.com/oauth2/v2/token";
    final private String PUSH_URL;
    final private ZoneOffset localZone = ZoneOffset.of("+8");

    public PushClient(String appId, String appKey) throws UnsupportedEncodingException {
        this.appId = appId;
        this.appKey = appKey;
        String nspCtx = "{\"ver\":\"1\", \"appId\":\"" + appId + "\"}";
        this.PUSH_URL = "https://api.push.hicloud.com/pushsend.do?nsp_ctx=" + URLEncoder.encode(nspCtx, "UTF-8");
    }

    @Override
    public void connect(IConnectionStatusListener listener) throws Exception {
        httpclient = HttpClients.createDefault();
        updateAccessToken(listener);
    }

    private void updateAccessToken(IConnectionStatusListener listener) {
        try {
            HttpPost post = new HttpPost(TOKEN_URL);
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
            } else {
                logger.warn("Get huawei access token failed: {}", body);
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

    @Override
    public void push(String session, PushEvent event, IConnectionStatusListener connListener, IPushStatusListener statusListener) {
        if (event.testEvent) {
            statusListener.onComplete(event, PushStatus.PUSH_SUCCEED);
            return;
        }
        try {
            HttpPost post = new HttpPost(PUSH_URL);
            post.addHeader("Content-Type", "application/x-www-form-urlencoded");
            LocalDateTime dt = LocalDateTime.ofEpochSecond(event.expiredTime/1000, 0, localZone);
            String expireTime = dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            StringBuilder tokensBuff = new StringBuilder();
            tokensBuff.append("[");
            for (String t : event.tokens) {
                tokensBuff.append(t);
                tokensBuff.append(",");
            }
            tokensBuff.setCharAt(tokensBuff.length() - 1, ']');
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("access_token", this.accessToken));
            params.add(new BasicNameValuePair("nsp_svc", "openpush.message.api.send"));
            params.add(new BasicNameValuePair("nsp_ts", Long.toString(System.currentTimeMillis()/1000)));
            params.add(new BasicNameValuePair("device_token_list", tokensBuff.toString()));
            params.add(new BasicNameValuePair("payload", event.payload));
            params.add(new BasicNameValuePair("expire_time", expireTime));
            post.setEntity(new UrlEncodedFormEntity(params));
            CloseableHttpResponse response = httpclient.execute(post);
            int code = response.getStatusLine().getStatusCode();
            String nspStatus = response.getFirstHeader("NSP_STATUS").getValue();
            if (code == 503) { //系统级失败：流控
                logger.warn("华为推送流控错误");
                statusListener.onComplete(event, PushStatus.PUSH_FAILD);
            } else if (code != 200) { //系统级失败：通讯错误
                logger.warn("华为推送错误, statusCode={}", code);
                statusListener.onComplete(event, PushStatus.PUSH_FAILD);
                connListener.onFailed();
            } else if (nspStatus != null && !"0".equals(nspStatus)) { // 系统级失败
                logger.warn("华为推送错误, nspStatus={}", nspStatus);
                statusListener.onComplete(event, PushStatus.PUSH_FAILD);
                connListener.onFailed();
            } else {
                String body = readBody(response);
                Map map = objectMapper.readValue(body, Map.class);
                String scode = (String) map.get("code");
                if ("80000000".equals(scode)) { //成功
                    statusListener.onComplete(event, PushStatus.PUSH_SUCCEED);
                } else { //应用级失败
                    statusListener.onComplete(event, PushStatus.PUSH_FAILD);
                }
            }
        } catch (Exception ex) {
            logger.warn("huawei push failed", ex);
            statusListener.onComplete(event, PushStatus.PUSH_FAILD);
            connListener.onFailed();
        }
    }

    @Override
    public void ping(String session, IConnectionStatusListener listener) {
        long now = System.currentTimeMillis();
        if (this.accessTokenExpiresTime - now < 600000) {
            updateAccessToken(listener);
        }
    }

    @Override
    public boolean isAvailable(String session) {
        long now = System.currentTimeMillis();
        return httpclient != null && this.accessTokenExpiresTime - now > 300000;
    }

    @Override
    public void close(String session) {
        if (httpclient != null) {
            try {
                httpclient.close();
            } catch (IOException e) {
                logger.warn("Close connection failed", e);
            }
        }
        httpclient = null;
    }

    public static void main(String[] args) {
        try {
            PushClient client = new PushClient("1091311", "a6g5b9xt14vr2uhv41d7ku8b5ml8z0a4");
            IConnectionStatusListener connListener = new IConnectionStatusListener() {
                @Override
                public void onSucceed() {
                    logger.info("onSucceed");
                }
                @Override
                public void onFailed() {
                    logger.info("onFailed");
                }
                @Override
                public void reconnect() {
                    logger.info("reconnect");
                }
                @Override
                public void connected(Object token) {
                    logger.info("connected, huawei access token: {}", token);
                }
            };
            client.connect(connListener);
            Thread.sleep(5000);
            client.close("");
        } catch (Exception ex) {
            logger.error(ex);
        }
    }

}