package net.arksea.pusher;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.japi.Creator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.duration.Duration;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2018/10/26.
 */
public class PushActor<T> extends AbstractActor {
    private final static Logger logger = LogManager.getLogger(PushActor.class);
    private static final int BACKOFF_MAX = 300000; //重连退避
    private static final int BACKOFF_MIN = 3000;
    private static final int PING_DELAY_SECONDS = 5;
    private int connectionFailedCount = 0;
    private IConnectionStatusListener connStatusListener;
    private final State<T> state;
    private T session;
    private Cancellable pingTimer;
    private Cancellable delayConnectTimer;
    public PushActor(State state) {
        this.state = state;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(AvailableAsk.class, this::handleAvailableAsk)
            .match(PushEvent.class,      this::handlePushEvent)
            .match(Ping.class,           this::handlePing)
            .match(Connect.class,        this::handleConnect)
            .match(Reconnect.class,      this::handleReconnect)
            .match(ConnectSucceed.class, this::handleConnectSucceed)
            .match(ConnectionSucceed.class, this::handleConnectionSucceed)
            .match(ConnectionFailed.class, this::handleConnectionFailed)
            .build();
    }

    static class State<T> {
        final IPushClient<T> pushClient;
        int connectDelay;
        final String pushActorName;
        final IPushStatusListener pushStatusListener;

        public State(String pushActorName, IPushClient<T> pushClient, IPushStatusListener pushStatusListener) {
            this.pushActorName = pushActorName;
            this.pushClient = pushClient;
            this.pushStatusListener = pushStatusListener;
        }
    }

    public static <SessionType> Props props(String pusherName, IPushClient<SessionType> pushClient, IPushStatusListener pushStatusListener) throws Exception {
        State<SessionType> state = new State<>(pusherName, pushClient, pushStatusListener);
        return Props.create(PushActor.class, (Creator<PushActor>) () -> new PushActor(state));
    }
    //------------------------------------------------------------------------------------
    @Override
    public void preStart() throws Exception {
        super.preStart();
        logger.debug("PushActor started: {}", state.pushActorName);
        connStatusListener = new ConnectionStatusListener(self());
        delayConnect();
        pingTimer = context().system().scheduler().schedule(
            Duration.create(PING_DELAY_SECONDS,TimeUnit.SECONDS),
            Duration.create(PING_DELAY_SECONDS,TimeUnit.SECONDS),
            self(),new Ping(),context().dispatcher(),self());
    }
    private void delayConnect() throws Exception {
        //null判断用于防止多次重复调用reconnect()引起不必要的频繁重连（多次通讯失败的回调可能会集中在一个时间点发生）
        if (delayConnectTimer == null) {
            state.pushClient.close(session);
            int backoff = state.connectDelay;
            state.connectDelay = Math.max(BACKOFF_MIN, Math.min(backoff * 2, BACKOFF_MAX));
            if (backoff == 0) {
                connect();
            } else {
                delayConnectTimer = context().system().scheduler().scheduleOnce(
                    Duration.create(backoff, TimeUnit.MILLISECONDS),
                    self(), new Connect(), context().dispatcher(), self());
            }
        }
    }
    //------------------------------------------------------------------------------------
    @Override
    public void postStop() throws Exception {
        super.postStop();
        logger.debug("PushActor stopped: {}", state.pushActorName);
        if (pingTimer != null) {
            pingTimer.cancel();
        }
        state.pushClient.close(session);
    }
    //------------------------------------------------------------------------------------
    private void handlePushEvent(PushEvent event) {
        logger.trace("call handlePushEvent() start");
        if (isAvailable()) {
            sender().tell(true, self()); //返回状态放在PushClient.push前，防止因其是阻塞类型的实现而影响吞吐率，以及导致超时造成的重复提交
            state.pushClient.push(session, event, connStatusListener, state.pushStatusListener);
        } else {
            sender().tell(false, self());
        }
    }
    //------------------------------------------------------------------------------------
    private void handleReconnect(Reconnect msg) throws Exception {
        logger.trace("call handleReconnect()");
        delayConnect();
    }
    //------------------------------------------------------------------------------------
    private static class Connect {}
    private void handleConnect(Connect msg) throws Exception {
        logger.trace("call handleConnect()");
        delayConnectTimer = null;
        connect();
    }
    private void connect() throws Exception {
        state.pushClient.connect(connStatusListener);
    }

    //------------------------------------------------------------------------------------
    private void handleAvailableAsk(AvailableAsk msg) {
        if (isAvailable() && System.currentTimeMillis() - msg.time < ASK_AVAILABLE_TIMEOUT) {
            sender().tell(new AvailableReply(self()), self());
        }
    }
    private boolean isAvailable() {
        return state.pushClient.isAvailable(session);
    }
    //------------------------------------------------------------------------------------
    private static class Ping {}
    private void handlePing(Ping msg) throws Exception {
        if (state.pushClient.isAvailable(session)) {
            state.pushClient.ping(session, connStatusListener);
        }
    }

    private void handleConnectionFailed(ConnectionFailed msg) throws Exception {
        if (++connectionFailedCount >= 3) {
            connectionFailedCount = 0;
            delayConnect();
        }
    }

    private void handleConnectionSucceed(ConnectionSucceed msg) throws Exception {
        connectionFailedCount = 0;
    }
    //------------------------------------------------------------------------------------
    private void handleConnectSucceed(ConnectSucceed<T> msg) {
        this.session = msg.session;
        //重置连接的退避时间
        state.connectDelay = BACKOFF_MIN;
    }

    public static final long ASK_AVAILABLE_TIMEOUT = 100; //ms
    public static class AvailableAsk {
        public final long time;
        public AvailableAsk() {
            time = System.currentTimeMillis();
        }
    }

    public static class AvailableReply {
        public final ActorRef pushActor;

        public AvailableReply(ActorRef pushActor) {
            this.pushActor = pushActor;
        }
    }

}
