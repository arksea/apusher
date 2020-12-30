package net.arksea.pusher.aliyun;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.aliyuncs.push.model.v20160801.PushRequest;
import com.aliyuncs.push.model.v20160801.PushResponse;
import com.aliyuncs.utils.ParameterHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.arksea.pusher.IConnectionStatusListener;
import net.arksea.pusher.IPushClient;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.PushEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2019/09/55.
 */
public class PushClient implements IPushClient<String> {
    private final static Logger logger = LogManager.getLogger(PushClient.class);
    private final static ObjectMapper objectMapper = new ObjectMapper();
    private final long appKey;
    private final IAcsClient client;
    private final long BACKOFF_MIN = 1000;
    private final long BACKOFF_MAX = 8000;
    private long backoff = BACKOFF_MIN;

    public PushClient(String accessKeyId, String accessKeySecret, long appKey, String region) {
        this.appKey = appKey;
        IClientProfile profile = DefaultProfile.getProfile(region, accessKeyId, accessKeySecret);
        client = new DefaultAcsClient(profile);
    }

    @Override
    public void connect(IConnectionStatusListener listener) {
        listener.connected("");
    }

    @Override
    public void push(String session, PushEvent event, IConnectionStatusListener connListener, IPushStatusListener statusListener) {
        long start = System.currentTimeMillis();
        if (event.testEvent) {
            statusListener.onPushSucceed(event, event.tokens.length);
            return;
        }
        try {
            PushRequest pushRequest = new PushRequest();
            pushRequest.setIOSApnsEnv("PRODUCT");//iOS的通知是通过APNs中心来发送的，需要填写对应的环境信息。'DEV': 表示开发环境 'PRODUCT': 表示生产环境
            pushRequest.setStoreOffline(true); // 离线消息是否保存,若保存, 在推送时候，用户即使不在线，下一次上线则会收到
            final String expireTime = ParameterHelper.getISO8601Time(new Date(System.currentTimeMillis() + 4 * 3600 * 1000)); // 4小时后消息失效, 不会再发送
            pushRequest.setExpireTime(expireTime);
            initRequest(event.payload, pushRequest);
            pushRequest.setAppKey(appKey);
            if ("DEVICE".equals(pushRequest.getTarget())) {
                String devices = StringUtils.join(event.tokens,',');
                pushRequest.setTargetValue(devices);
            }
            PushResponse pushResponse = client.getAcsResponse(pushRequest);
            statusListener.onPushSucceed(event, event.tokens.length);
            //记录费时推送用于观察
            long time = System.currentTimeMillis() - start;
            if (time > 1000) {
                logger.info("Aliyun push succeed, use time={}ms, client={}", time, this);
            } else {
                logger.debug("Aliyun push succeed, use time={}ms, client={}", time, this);
            }
            if (backoff > BACKOFF_MIN){
                backoff = Math.max(BACKOFF_MIN, backoff - 1000);
            }
        } catch (Exception ex) {
            long time = System.currentTimeMillis() - start;
            String err = ex.getMessage();
            logger.warn("Aliyun push failed, time={}ms, {}, client={}", time, err, this);
            statusListener.onPushFailed(event, event.tokens.length);
            connListener.onFailed();
            //遇到因繁忙失败时进行退避延时
            if (err.startsWith("Throttling.User") || err.startsWith("ServiceUnavailable")) {
                sleep(backoff);
                backoff = Math.min(backoff * 2, BACKOFF_MAX);
            }
        }
    }

    private void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (Exception ex) {
            logger.warn("Sleep error", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static void initRequest(String payload, PushRequest pushRequest) throws IOException {
        Map<String,Object> map = objectMapper.readValue(payload,(Class<Map<String,Object>>)(Class<?>)Map.class);
        map.forEach((k,v) -> {
            try {
                String name = "set" + k;
                Method method;
                if ("AppKey".equals(k)) {
                    method = PushRequest.class.getMethod(name, Long.class);
                    long value = (Integer) v;
                    method.invoke(pushRequest, value);
                } else if (v instanceof Long) {
                    method = PushRequest.class.getMethod(name, Long.class);
                    method.invoke(pushRequest, (Long) v);
                } else if (v instanceof Integer) {
                    method = PushRequest.class.getMethod(name, Integer.class);
                    method.invoke(pushRequest, (Integer) v);
                } else if (v instanceof String) {
                    method = PushRequest.class.getMethod(name, String.class);
                    method.invoke(pushRequest, (String) v);
                } else if (v instanceof Boolean) {
                    method = PushRequest.class.getMethod(name, Boolean.class);
                    method.invoke(pushRequest, (Boolean) v);
                } else {
                    logger.warn("unsupport parameter type:" + v.getClass());
                }
                logger.debug("set aliyun push param succeed: {}={}", k, v);
            } catch (Exception ex) {
                logger.warn("set aliyun push param failed: {}={}", k, v, ex);
            }
        });
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
        Configurator.initialize("", ".\\pusher-aliyun\\src\\test\\resources\\log4j2-test.xml");
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
                logger.info("推送失败: rate limit");
            }
            @Override
            public void handleInvalidToken(String token) {
                logger.info("Invalid Token: {}", token);
            }
        };

        final String[] tokens = new String[] {"123456"};

        final long appKey = 123;
        final String accessKeyId = "456";
        final String accessKeySecret = "789";

        final String payload = "{\"Target\":\"DEVICE\",\"DeviceType\":\"ANDROID\",\"PushType\":\"NOTICE\",\"Title\":\"Hello\",\"Body\":\"PushRequest Body\"}";
        final PushClient client = new PushClient(accessKeyId, accessKeySecret, appKey,"cn-hangzhou");
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
