package net.arksea.pusher.baidu;

import com.baidu.yun.core.log.YunLogEvent;
import com.baidu.yun.core.log.YunLogHandler;
import com.baidu.yun.push.auth.PushKeyPair;
import com.baidu.yun.push.client.BaiduPushClient;
import com.baidu.yun.push.constants.BaiduPushConstants;
import com.baidu.yun.push.exception.PushClientException;
import com.baidu.yun.push.exception.PushServerException;
import com.baidu.yun.push.model.*;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.PushEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * Created by xiaohaixing on 2018/09/17.
 */
public class BaiduClientUtils {
    private static final Logger log = LogManager.getLogger(BaiduClientUtils.class);

    public static BaiduPushClient create(String apiKey, String secretKey) {
        PushKeyPair pair = new PushKeyPair(apiKey,secretKey);
        return new BaiduPushClient(pair, BaiduPushConstants.CHANNEL_REST_URL);
    }

    public static String push(BaiduPushClient client, PushEvent event, Set<String> passthroughPayload, IPushStatusListener statusListener) {//throws Exception {
        if (event.testEvent) {
            statusListener.onSucceed(event);
            return null;
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
            PushMsgToSingleDeviceResponse response = client.pushMsgToSingleDevice(request);
            log.debug("msgId="+response.getMsgId()+",sendTime="+response.getSendTime());
            statusListener.onSucceed(event);
            return response.getMsgId();
        } catch (PushClientException ex) {
            statusListener.onFailed(-1, ex, event);
        } catch (PushServerException ex) {
            int code = ex.getErrorCode();
            if (code == 30605 || code == 30607 || code == 30608 || code == 30609) {
                statusListener.onFailed(200, "BadDeviceToken", event); //默认都作为BadDeviceToken处理
            } else {
                String err = "errorCode=" + ex.getErrorCode()
                           + ", errorMsg=" + ex.getErrorMsg()
                           + ", requestId=" + ex.getRequestId();
                statusListener.onFailed(200, new Exception(err,ex), event);
            }
        } catch (Exception ex) {
            log.error("Unknown Error", ex);
        }
        return null;
    }

    public static void main(String[] args) {
        try {
            BaiduPushClient client = create("lQ3sLrgC09CuS10vlNOarUCH", "QyhNvF2IoVAzhFfgL6uxGBNCT3QyCaIV");
            client.setChannelLogHandler(new YunLogHandler() {
                @Override
                public void onHandle(YunLogEvent yunLogEvent) {
                    log.error(yunLogEvent.getMessage());
                }
            });
            PushEvent event = new PushEvent("1", "topic", "4221746755194810045",//"4368517502480824174", //"4221746755194810045",
                "{\"title\":\"hello7\",\"description\":\"hello world\"}", "info",
                System.currentTimeMillis()+3600_000);
            Thread.sleep(3000);
            Set<String> passthroughPayload = new HashSet<>();
            passthroughPayload.add("info");
            push(client, event, passthroughPayload, new IPushStatusListener() {
                @Override
                public void onSucceed(PushEvent event) {
                    log.error("onSucceed");
                }
                @Override
                public void onFailed(int status, Object reason, PushEvent event) {
                    if (reason instanceof Throwable) {
                        log.error("onFailed", reason);
                    } else {
                        log.error("onFailed: {}", reason);
                    }
                }
            });
            Thread.sleep(20000);
            log.error("exit pusher");
        } catch (Exception ex) {
            log.error("Error", ex);
        }
    }
}