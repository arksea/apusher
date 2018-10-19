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

    private final State state;

    private HTTP2Client apnsClient;
    private Session session;
    private Cancellable pingTimer;
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
            .match(DelayConnect.class,   this::handleDelayConnect)
            .match(ConnectSucceed.class, this::handleConnectSucceed)
            .match(CreateClient.class,   this::handleCreateClient)
            .build();
    }

    static class State {
        int connectDelay = BACKOFF_MIN;
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
        pingTimer = context().system().scheduler().schedule(
            Duration.create(PING_DELAY_SECONDS,TimeUnit.SECONDS),
            Duration.create(PING_DELAY_SECONDS,TimeUnit.SECONDS),
            self(),new Ping(),context().dispatcher(),self());
        createApnsClient();
    }

    //------------------------------------------------------------------------------------
    private void createApnsClient() {
        try {
            stopApnsClient();
            apnsClient = ApnsClientUtils.create(clientLifeCycleListener);
            state.connectDelay = BACKOFF_MIN; //连接成功重置退避时间
            delayConnect();
        } catch (Exception ex) {
            logger.warn("create ApnsClient failed: {}",state.pushActorName,  ex);
            delayCreateClient();
        }
    }
    private void delayCreateClient() {
        logger.trace("call delayCreateClient()");
        int backoff = state.connectDelay;
        state.connectDelay = Math.min(backoff*2, BACKOFF_MAX);
        context().system().scheduler().scheduleOnce(
            Duration.create(backoff, TimeUnit.MILLISECONDS),
            self(), new CreateClient(), context().dispatcher(), self());
    }
    private static class CreateClient {
    }
    private void handleCreateClient(CreateClient msg) {
        logger.trace("call handleCreateClient()");
        createApnsClient();
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
    private static class DelayConnect {}
    private void handleDelayConnect(DelayConnect msg) {
        logger.trace("call handleDelayConnect()");
        if (session != null) {
            session.close(1, null, new Callback() {});
            session = null;
        }
        int backoff = state.connectDelay;
        state.connectDelay = Math.min(backoff*2, BACKOFF_MAX);
        context().system().scheduler().scheduleOnce(
                Duration.create(backoff, TimeUnit.MILLISECONDS),
                self(), new Connect(), context().dispatcher(), self());
    }
    private void delayConnect() {
        logger.trace("call delayConnect()");
        self().tell(new DelayConnect(), ActorRef.noSender());
    }
    //------------------------------------------------------------------------------------
    private static class Connect {}
    private void handleConnect(Connect msg) {
        logger.trace("call handleConnect()");
        connect();
    }

    private void connect() {
        ApnsClientUtils.connect(state.apnsServerIP, apnsClient, state.keyManagerFactory,
                new Session.Listener.Adapter() {
                    @Override
                    public void onReset(Session session, ResetFrame frame) {
                        logger.warn("ApnsHtt2Client session onReset: {}", state.pushActorName);
                        delayConnect();
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
                        delayConnect();
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
                        delayConnect();
                    }
                });
    }

    //------------------------------------------------------------------------------------
    public static class AvailableReply {}
    private void handleAvailableReply(AvailableReply msg) {
        sender().tell(isAvailable(), self());
    }
    private boolean isAvailable() {
        return apnsClient != null && session != null  && ApnsClientUtils.isAvailable(session)
        && apnsClient.isRunning() && !apnsClient.isFailed();
    }
    //------------------------------------------------------------------------------------
    private static class Ping {}
    private void handlePing(Ping msg) {
        if (apnsClient != null && session != null && apnsClient.isRunning() && !apnsClient.isFailed()) {
            ApnsClientUtils.ping(session, new Callback() {
                public void failed(Throwable ex) {
                    logger.warn("ApnsHtt2Client session ping failed: {}", state.pushActorName, ex);
                    delayConnect();
                }
            });
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
