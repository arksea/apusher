package net.arksea.pusher.xinge;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
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
import org.apache.logging.log4j.core.config.Configurator;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2019/09/55.
 */
public class PushClient implements IPushClient<String> {
    private final static Logger logger = LogManager.getLogger(PushClient.class);
    private final static ObjectMapper objectMapper = new ObjectMapper();
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
            Map bodyMap = new HashMap();
            bodyMap.put("audience_type", event.tokens.length > 1 ? "token_list" : "token");
            bodyMap.put("token_list", event.tokens);
            bodyMap.put("platform", "android");
            bodyMap.put("message",  objectMapper.readValue(event.payload, Map.class));
            bodyMap.put("message_type", "notify");
            String postBody = objectMapper.writeValueAsString(bodyMap);
            HttpEntity entity = new StringEntity(postBody, "UTF-8");
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
    }

    @Override
    public boolean isAvailable(String session) {
        return true;
    }

    @Override
    public void close(String session) {
    }

    public static void main(String[] args) throws Exception {
        Configurator.initialize("", "./push-server/src/test/resources/log4j2-test.xml");
        IPushStatusListener pl = new IPushStatusListener() {
            @Override
            public void onPushSucceed(PushEvent event, int succeedCount) {
                logger.info("推送成功");
            }
            @Override
            public void onPushFailed(PushEvent event, int failedCount) {
                logger.info("推送失败");
            }
            @Override
            public void onRateLimit(PushEvent event) {
                logger.info("推送失败");
            }
            @Override
            public void handleInvalidToken(String token) {
                logger.info("Invalid Token: {}", token);
            }
        };

        final String[] tokens = new String[] {"6998779b7446c6974485b160e726c98ff61892ac"};
        final String appId = "111";
        final String appKey = "222";
        final String postmanToken = "333";
        final String payload = "{\"title\":\"2015-2019很可能是史上最热五年\",\"content\":\"极端高温事件频发，预计今年夏天北半球会出现更多热浪>>\",\"xg_media_resources\":\"http://bos.tq.ifjing.com/zxht/pic/20190701174005_17562.jpg\",\"android\":{\"action\":{\"action_type\":3,\"intent\":\"hlrlfl://b3BlbmFwcD9wYXJhbXM9aHR0cCUzQSUyRiUyRnRxLmlmamluZy5jb20lMkZzdGF0aWMlMkZhcHAlMkZ3ZWF0aGVyJTJGdG9waWMlMkZpbmZvLmh0bWwlM0ZhY3QlM0Q1MDUlMjZjQWN0JTNENCUyNmlkJTNEODYxMjE2MyUyNm1vZGVsJTNEc2Vhc29uJTI2aW5mb1RhZyUzRDEwMCUyNnNoYXJlVHlwZSUzRDQ=\"},\"custom_content\":{\"act\":\"http://tq.ifjing.com/static/app/weather/topic/info.html?act=505&cAct=4&id=8612163&model=season&infoTag=100&shareType=4\",\"stat\":{\"type\":7}}}}";
        final PushClient client = new PushClient(appId, appKey, postmanToken);
        PushEvent event = new PushEvent("1",
            "20000105",
            tokens,
            payload,
            "yunshi-test",
            System.currentTimeMillis()+3600_000);

        final IConnectionStatusListener cl = new IConnectionStatusListener() {
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
                logger.info("连接到推送服务器失败");
            }
            @Override
            public void connected(Object session) {
                logger.debug("connected: {}", session);
                client.push("", event, this, pl);
            }
        };

        client.connect(cl);
        Thread.sleep(20000);
    }
}
