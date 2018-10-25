package net.arksea.pusher.apns;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.japi.Creator;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.PushEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.frames.*;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.component.LifeCycle;
import scala.concurrent.duration.Duration;

import javax.net.ssl.KeyManagerFactory;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2017/11/8.
 */
public class ApnsPushActor extends AbstractActor {
    private final static Logger logger = LogManager.getLogger(ApnsPushActor.class);
    //重连退避
    private static final int BACKOFF_MAX = 300000;
    private static final int BACKOFF_MIN = 3000;

    private static final int PING_DELAY_SECONDS = 5;
    private int pingFailedCount = 0;
    private final State state;

    private HTTP2Client apnsClient;
    private Session session;
    private Cancellable pingTimer;
    private Cancellable delayConnectTimer;
    private LifeCycle.Listener clientLifeCycleListener;
    public ApnsPushActor(State state) {
        this.state = state;
         clientLifeCycleListener = new ClientLifeCycleListener(state.pushActorName);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(AvailableReply.class, this::handleAvailableReply)
            .match(PushEvent.class,      this::handleApnsEvent)
            .match(Ping.class,           this::handlePing)
            .match(Connect.class,        this::handleConnect)
            .match(Reconnect.class,   this::handleReconnect)
            .match(ConnectSucceed.class, this::handleConnectSucceed)
            .match(PingFailed.class, this::handlePingFailed)
            .build();
    }

    static class State {
        int connectDelay;
        final String pushActorName;
        final String apnsServerIP;
        final String apnsTopic;
        final KeyManagerFactory keyManagerFactory;
        final IPushStatusListener pushStatusListener;

        public State(String pushActorName, String apnsServerIP, String apnsTopic, KeyManagerFactory keyManagerFactory, IPushStatusListener pushStatusListener) {
            this.pushActorName = pushActorName;
            this.apnsServerIP = apnsServerIP;
            this.apnsTopic = apnsTopic;
            this.keyManagerFactory = keyManagerFactory;
            this.pushStatusListener = pushStatusListener;
        }
    }

    public static Props props(String pusherName, String apnsServerIP, String apnsTopic, KeyManagerFactory keyManagerFactory, IPushStatusListener pushStatusListener) throws Exception {
        State state = new State(pusherName, apnsServerIP, apnsTopic, keyManagerFactory, pushStatusListener);
        return Props.create(ApnsPushActor.class, new Creator<ApnsPushActor>() {
            @Override
            public ApnsPushActor create() throws Exception {
                return new ApnsPushActor(state);
            }
        });
    }
    //------------------------------------------------------------------------------------
    @Override
    public void preStart() throws Exception {
        super.preStart();
        logger.debug("ApnsPushActor started: {}", state.pushActorName);
        delayConnect();
    }
    private void delayConnect() throws Exception {
        //null判断用于防止多次重复调用reconnect()引起不必要的频繁重连（多次通讯失败的回调可能会集中在一个时间点发生）
        if (delayConnectTimer == null) {
            stopApnsClient();
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
        logger.debug("ApnsPushActor stopped: {}", state.pushActorName);
        if (pingTimer != null) {
            pingTimer.cancel();
        }
        stopApnsClient();
    }
    private void stopApnsClient() {
        closeSession();
        if (apnsClient != null) {
            try {
                apnsClient.stop();
            } catch (Exception ex) {
                logger.warn("Close apns client failed: {}", state.pushActorName, ex);
            }
            apnsClient = null;
        }
    }
    private void closeSession() {
        if (session != null) {
            session.close(1, null, new Callback() {});
            session = null;
        }
    }
    //------------------------------------------------------------------------------------
    private void handleApnsEvent(PushEvent event) {
        logger.trace("call handleApnsEvent()");
        if (isAvailable()) {
            ApnsClientUtils.push(session, state.apnsTopic, event, state.pushStatusListener);
            sender().tell(true, self());
        } else {
            sender().tell(false, self());
        }
    }

    //------------------------------------------------------------------------------------
    private static class Reconnect {}
    private void handleReconnect(Reconnect msg) throws Exception {
        logger.trace("call handleReconnect()");
        delayConnect();
    }
    private void reconnect() {
        logger.trace("call reconnect()");
        self().tell(new Reconnect(), ActorRef.noSender());
    }
    //------------------------------------------------------------------------------------
    private static class Connect {}
    private void handleConnect(Connect msg) throws Exception {
        logger.trace("call handleConnect()");
        delayConnectTimer = null;
        connect();
    }
    private void connect() throws Exception {
        apnsClient = ApnsClientUtils.create(clientLifeCycleListener);
        ApnsClientUtils.connect(state.apnsServerIP, apnsClient, state.keyManagerFactory,
                new Session.Listener.Adapter() {
                    @Override
                    public void onReset(Session session, ResetFrame frame) {
                        logger.warn("ApnsHtt2Client session onReset: {}", state.pushActorName);
                        reconnect();
                    }

                    @Override
                    public void onClose(Session session, GoAwayFrame frame) {
                        logger.warn("ApnsHtt2Client session onClose: {}", state.pushActorName);
                    }

                    @Override
                    public boolean onIdleTimeout(Session session) {
                        logger.warn("ApnsHtt2Client session onIdleTimeout: {}", state.pushActorName);
                        return false; //不关闭连接
                    }

                    @Override
                    public void onFailure(Session session, Throwable failure) {
                        logger.warn("ApnsHtt2Client session onFailure: {}", state.pushActorName, failure);
                        reconnect();
                    }

                    @Override
                    public void onPing(Session session, PingFrame frame) {
                        if (!frame.isReply()) {
                            logger.warn("ApnsHtt2Client session received ping frame: {}", state.pushActorName);
                        }
                    }
                },
                new Promise<Session>() {
                    @Override
                    public void succeeded(Session result) {
                        logger.trace("ApnsHtt2Client connect succeed: {}", state.pushActorName);
                        self().tell(new ConnectSucceed(result), ActorRef.noSender());
                    }

                    @Override
                    public void failed(Throwable x) {
                        logger.warn("ApnsHtt2Client connect failed: {}", state.pushActorName, x);
                        reconnect();
                    }
                });
    }

    //------------------------------------------------------------------------------------
    public static class AvailableReply {}
    private void handleAvailableReply(AvailableReply msg) {
        if (isAvailable()) {
            sender().tell(self(), self());
        }
    }
    private boolean isAvailable() {
        return apnsClient != null && session != null  && ApnsClientUtils.isAvailable(session)
        && apnsClient.isRunning() && !apnsClient.isFailed();
    }
    //------------------------------------------------------------------------------------
    private static class Ping {}
    private void handlePing(Ping msg) {
        ActorRef actor = self();
        if (apnsClient != null && session != null && apnsClient.isRunning() && !apnsClient.isFailed()) {
            ApnsClientUtils.ping(session, new Callback() {
                public void failed(Throwable ex) {
                    logger.warn("ApnsHtt2Client session ping failed: {}", state.pushActorName, ex);
                    actor.tell(new PingFailed(), ActorRef.noSender());
                }
            });
        }
    }

    private static class PingFailed {}
    private void handlePingFailed(PingFailed msg) throws Exception {
        if (++pingFailedCount >= 3) {
            pingFailedCount = 0;
            delayConnect();
        }
    }
    //------------------------------------------------------------------------------------
    private static class ConnectSucceed {
        public final Session session;
        public ConnectSucceed(Session session) {
            this.session = session;
        }
    }
    private void handleConnectSucceed(ConnectSucceed msg) {
        this.session = msg.session;
        //重置连接的退避时间
        state.connectDelay = BACKOFF_MIN;
        pingTimer = context().system().scheduler().schedule(
            Duration.create(PING_DELAY_SECONDS,TimeUnit.SECONDS),
            Duration.create(PING_DELAY_SECONDS,TimeUnit.SECONDS),
            self(),new Ping(),context().dispatcher(),self());
    }

    static class ClientLifeCycleListener implements LifeCycle.Listener {
        private final String pushActorName;
        public ClientLifeCycleListener(String pushActorName) {
            this.pushActorName = pushActorName;
        }

        @Override
        public void lifeCycleStarting(LifeCycle event) {
            logger.trace("ApnsHtt2Client lifeCycleStarting: {}", pushActorName);
        }

        @Override
        public void lifeCycleStarted(LifeCycle event) {
            logger.trace("ApnsHtt2Client lifeCycleStarted: {}", pushActorName);
        }

        @Override
        public void lifeCycleFailure(LifeCycle event, Throwable cause) {
            logger.warn("ApnsHtt2Client lifeCycleFailure: {}", pushActorName, cause);
        }

        @Override
        public void lifeCycleStopping(LifeCycle event) {
            logger.trace("ApnsHtt2Client lifeCycleStopping: {}", pushActorName);
        }

        @Override
        public void lifeCycleStopped(LifeCycle event) {
            logger.trace("ApnsHtt2Client lifeCycleStoped: {}", pushActorName);
        }
    };
}
