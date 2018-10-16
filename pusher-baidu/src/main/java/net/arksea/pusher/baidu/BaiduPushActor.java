package net.arksea.pusher.baidu;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.Creator;
import com.baidu.yun.core.log.YunLogEvent;
import com.baidu.yun.core.log.YunLogHandler;
import com.baidu.yun.push.client.BaiduPushClient;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.PushEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.duration.Duration;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2017/11/8.
 */
public class BaiduPushActor extends AbstractActor {
    private final static Logger logger = LogManager.getLogger(BaiduPushActor.class);
    //重连退避
    private static final int BACKOFF_MAX = 300000;
    private static final int BACKOFF_MIN = 3000;

    private final State state;

    private BaiduPushClient pushClient;
    private YunLogHandler yunLogHandler;

    public BaiduPushActor(State state) {
        this.state = state;
         yunLogHandler = new YunLogHandler() {
             @Override
             public void onHandle(YunLogEvent yunLogEvent) {
                 logger.debug(yunLogEvent.getMessage());
             }
         };
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(AvailableReply.class, this::handleAvailableReply)
            .match(PushEvent.class,      this::handlePushEvent)
            .match(Connect.class,        this::handleConnect)
            .build();
    }

    static class State {
        int connectDelay = BACKOFF_MIN;
        final String pushActorName;
        final String apiKey;
        final String secretKey;
        final Set<String> passthroughPayload; //以透传方式推送的payload类型集合
        final IPushStatusListener pushStatusListener;

        public State(String pushActorName, String apiKey, String secretKey, Set<String> passthroughPayload, IPushStatusListener pushStatusListener) {
            this.pushActorName = pushActorName;
            this.apiKey = apiKey;
            this.secretKey = secretKey;
            this.passthroughPayload = passthroughPayload;
            this.pushStatusListener = pushStatusListener;
        }
    }

    public static Props props(String pusherName, String apiKey, String secretKey, Set<String> passthroughPayload, IPushStatusListener pushStatusListener) throws Exception {
        State state = new State(pusherName, apiKey, secretKey, passthroughPayload, pushStatusListener);
        return Props.create(BaiduPushActor.class, new Creator<BaiduPushActor>() {
            @Override
            public BaiduPushActor create() throws Exception {
                return new BaiduPushActor(state);
            }
        });
    }
    //------------------------------------------------------------------------------------
    @Override
    public void preStart() throws Exception {
        super.preStart();
        logger.debug("BaiduPushActor started: {}", state.pushActorName);
        state.connectDelay = BACKOFF_MIN;
        delayConnect();
    }
    //------------------------------------------------------------------------------------
    private void delayConnect() {
        logger.trace("call delayConnect()");
        int backoff = state.connectDelay;
        state.connectDelay = Math.min(backoff*2, BACKOFF_MAX);
        context().system().scheduler().scheduleOnce(
            Duration.create(backoff, TimeUnit.MILLISECONDS),
            self(), new Connect(), context().dispatcher(), self());
    }
    //------------------------------------------------------------------------------------
    private static class Connect {}
    private void handleConnect(Connect msg) {
        logger.trace("call handleConnect()");
        connect();
    }
    private void connect() {
        try {
            stopPushClient();
            pushClient = BaiduClientUtils.create(state.apiKey, state.secretKey);
            pushClient.setChannelLogHandler(yunLogHandler);
            state.connectDelay = BACKOFF_MIN; //连接成功重置退避时间
        } catch (Exception ex) {
            logger.warn("create BaiduPushClient failed: {}",state.pushActorName,  ex);
            delayConnect();
        }
    }
    //------------------------------------------------------------------------------------
    @Override
    public void postStop() throws Exception {
        super.postStop();
        logger.debug("BaiduPushActor stopped: {}", state.pushActorName);
        stopPushClient();
    }
    private void stopPushClient() {
        if (pushClient != null) {
            pushClient = null;
        }
    }
    //------------------------------------------------------------------------------------
    private void handlePushEvent(PushEvent event) {
        logger.trace("call handlePushEvent()");
        if (isAvailable()) {
            BaiduClientUtils.push(pushClient, event, state.passthroughPayload, state.pushStatusListener);
            sender().tell(true, self());
        } else {
            sender().tell(false, self());
        }
    }
    //------------------------------------------------------------------------------------
    public static class AvailableReply {}
    private void handleAvailableReply(AvailableReply msg) {
        if (isAvailable()) {
            sender().tell(self(), self());
        }
    }
    private boolean isAvailable() {
        return pushClient != null;
    }
}
