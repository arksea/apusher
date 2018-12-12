package net.arksea.pusher.apns;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.PushEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.util.Callback;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * Created by xiaohaixing on 2018/10/25.
 */
public class ResponseListener extends Stream.Listener.Adapter {
    private static final Logger log = LogManager.getLogger(ResponseListener.class);
    private static ObjectMapper objectMapper = new ObjectMapper();
    private final IPushStatusListener statusListener;
    private final PushEvent event;
    //原子操作是为了防止 onHeaders 和 onData不在同一个线程中回调
    private AtomicInteger status = new AtomicInteger(-1);
    //测试onHeaders 和 onData是否可能不在同一个线程中回调，目前没有观察到此现象
    private AtomicLong onHeaderThreadId = new AtomicLong(0);
    public ResponseListener(PushEvent event, IPushStatusListener statusListener) {
        this.statusListener = statusListener;
        this.event = event;
    }

    @Override
    public void onHeaders(Stream stream, HeadersFrame frame) {
        log.debug("StreamListener.onHeader(),{}",frame.getMetaData());
        MetaData meta = frame.getMetaData();
        if (meta.isResponse()) {
            MetaData.Response response = (MetaData.Response)meta;
            status.set(response.getStatus());
            onHeaderThreadId.set(Thread.currentThread().getId());
            if (response.getStatus() == 200) {
                statusListener.onPushSucceed(event, 1);
            }
            //此处不回调，留到onData里判断返回的错误reason后再回调
            //else {
            //statusListener.onFailed(event);
            //}
        } else {
            log.warn("header is not response"); //不会收到非Response的Header，目前未观察到例外
        }
    }

    @Override
    public void onData(Stream stream, DataFrame frame, Callback callback) {
        log.debug("StreamListener.onData()");
        if(onHeaderThreadId.get() != Thread.currentThread().getId()) {
            //onHeader 和 onData是同一个线程回调，目前没有观察到例外
            log.warn("OnHeaders() and onData() not in same Thread");
        }
        ByteBuffer buf = frame.getData();
        String body = Charset.forName("UTF-8").decode(buf).toString();
        String msg = "status="+status+";body="+body;
        if (status.get() == 200) {
            //status==200时不会有onData，目前未观察到例外
            log.warn("onData when succeed: {};eventId={}, topic={},token={}", msg,event.id, event.topic,event.tokens[0]);
            callback.succeeded();
        } else {
            try {
                Map ret = objectMapper.readValue(body, Map.class);
                String reason = (String)ret.get("reason");
                log.trace("apns push failed: {};eventId={},topic={},token={}",msg,event.id,event.topic,event.tokens[0]);
                onFailed(status.get(), reason, event);
            } catch (IOException ex) {
                log.error("parse apns resule failed: "+body, ex);
                onFailed(status.get(), ex, event);
            }
            callback.failed(new Exception(msg));
        }

    }

    private void onFailed(int status, Object reasonObj, PushEvent event) {
        if (reasonObj instanceof Throwable) {
            if (reasonObj instanceof IOException) {
                log.debug("push failed: eventId={},topic={}", event.id, event.topic, reasonObj);
            } else {
                log.warn("push failed: eventId={},topic={}", event.id, event.topic, reasonObj);
            }
            statusListener.onPushFailed(event, 1);
        } else {
            String reason = reasonObj.toString();
            if (status == -1 || status == 500 || status == 503) {
                log.warn("apns push failed: status={},reason={},eventId={},topic={}", status, reason, event.id, event.topic);
                statusListener.onPushFailed(event, 1);
            } else {
                switch (reason) {
                    case "DeviceTokenNotForTopic":
                    case "BadDeviceToken":
                    case "Unregistered":
                    case "ExpiredProviderToken":
                    case "InvalidProviderToken":
                    case "MissingProviderToken":
                    case "TooManyProviderTokenUpdates": //The provider token is being updated too often.
                    case "TooManyRequests":  //Too many requests were made consecutively to the same device token.
                        //这些状态表明是用户状态异常造成的推送失败，不做推送成功与失败计数
                        //并将tokenActive设置成false，下次不再向他推送
                        statusListener.onPushFailed(event, 1);
                        statusListener.handleInvalidToken(event.tokens[0]);
                        log.debug("apns push failed: status={},reason={},eventId={},topic={},token={}", status, reason, event.id, event.topic,event.tokens[0]);
                        break;
                    case "InternalServerError":
                    case "ServiceUnavailable":
                    case "Shutdown":
                    default:
                        log.warn("apns push failed: status={},reason={},eventId={},topic={}", status, reason, event.id, event.topic);
                        statusListener.onPushFailed(event, 1);
                        break;
                }
            }
        }
    }
}
