package net.arksea.pusher.baidu;

import com.baidu.yun.push.auth.PushKeyPair;
import com.baidu.yun.push.client.BaiduPushClient;
import com.baidu.yun.push.constants.BaiduPushConstants;
import com.baidu.yun.push.exception.PushClientException;
import com.baidu.yun.push.exception.PushServerException;
import com.baidu.yun.push.model.PushMsgToSingleDeviceRequest;
import com.baidu.yun.push.model.PushMsgToSingleDeviceResponse;
import net.arksea.pusher.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Set;

/**
 *
 * Created by xiaohaixing on 2018/10/26.
 */
public class PushClient implements IPushClient {
    private final static Logger logger = LogManager.getLogger(PushClient.class);

    private BaiduPushClient apnsClient;
    final private String apiKey;
    final private String secretKey;
    final private Set<String> passthroughPayload;

    public PushClient(String apiKey, String secretKey, Set<String> passthroughPayload) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.passthroughPayload = passthroughPayload;
    }

    @Override
    public void connect(IConnectionStatusListener listener) throws Exception {
        PushKeyPair pair = new PushKeyPair(apiKey,secretKey);
        apnsClient = new BaiduPushClient(pair, BaiduPushConstants.CHANNEL_REST_URL);
        listener.connected("connected");
        listener.onSucceed();
    }

    @Override
    public void push(Object session, PushEvent event, IConnectionStatusListener connListener, IPushStatusListener statusListener) {
        if (event.testEvent) {
            statusListener.onComplete(event, PushStatus.PUSH_SUCCEED);
            return;
        }
        try {
            int expiresSeconds = (int) (event.expiredTime - event.createTime) / 1000;
            expiresSeconds = Math.min(expiresSeconds, 604800);
            expiresSeconds = Math.max(1, expiresSeconds);
            PushMsgToSingleDeviceRequest request = new PushMsgToSingleDeviceRequest();
            int pushType = 1; //以notify方式推送
            if (passthroughPayload.contains(event.payloadType) || passthroughPayload.contains("all")) {
                pushType = 0; //以透传方式推送
            }
            request
                .addChannelId(event.token)
                .addMsgExpires(expiresSeconds)  //设置消息的有效时间,单位秒,默认3600*5.
                .addMessageType(pushType) //设置消息类型,0表示透传消息,1表示通知,默认为0.
                .addMessage(event.payload)
                .addDeviceType(3);      //设置设备类型，deviceType => 1 for web, 2 for pc, 3 for android, 4 for ios, 5 for wp.
            PushMsgToSingleDeviceResponse response = apnsClient.pushMsgToSingleDevice(request);
            logger.debug("msgId="+response.getMsgId()+",sendTime="+response.getSendTime());
            statusListener.onComplete(event, PushStatus.PUSH_SUCCEED);
        } catch (PushClientException ex) {
            logger.warn("push failed: eventId={},topic={}", event.id, event.topic, ex);
            statusListener.onComplete(event, PushStatus.PUSH_FAILD);
            connListener.onFailed();
        } catch (PushServerException ex) {
            logger.warn("push failed: eventId={},topic={},errorCode={}", event.id, event.topic,
                ex.getErrorCode(),ex);
            int code = ex.getErrorCode();
            if (code == 30605 || code == 30607 || code == 30608 || code == 30609) { //默认都作为INVALID_TOKEN处理
                statusListener.onComplete(event, PushStatus.INVALID_TOKEN);
            } else {
                statusListener.onComplete(event, PushStatus.PUSH_FAILD);
            }
        } catch (Exception ex) {
            statusListener.onComplete(event, PushStatus.PUSH_FAILD);
            connListener.onFailed();
            logger.error("Unknown Error", ex);
        }
    }

    @Override
    public void ping(Object session, IConnectionStatusListener listener) {
    }

    @Override
    public boolean isAvailable(Object session) {
        return apnsClient != null;
    }

    @Override
    public void close(Object session) {
        apnsClient = null;
    }
}
