package net.arksea.pusher.apns;

import net.arksea.pusher.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.frames.*;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 *
 * Created by xiaohaixing on 2018/10/26.
 */
public class PushClient implements IPushClient<Session> {
    private final static Logger logger = LogManager.getLogger(PushClient.class);
    private static final int MAX_STREAM_SIZE = 495;
    public static final String APNS_HOST = "api.push.apple.com";
    private static final int APNS_PORT = 443;
    private static final int APNS_DEV_PORT = 2197;
    private static final String URI_BASE = "https://" + APNS_HOST + ":" + APNS_PORT + "/3/device/";

    final private String name;
    final private LifeCycle.Listener clientLifeCycleListener;
    final private String apnsServerIP;
    final private KeyManagerFactory keyManagerFactory;
    final private String apnsTopic;
    private HTTP2Client apnsClient;

    public PushClient(String name, String apnsTopic, String apnsServerIP, KeyManagerFactory keyManagerFactory) {
        this.name = name;
        this.apnsTopic = apnsTopic;
        this.clientLifeCycleListener = new ClientLifeCycleListener(name);
        this.apnsServerIP = apnsServerIP;
        this.keyManagerFactory = keyManagerFactory;
    }

    public static HTTP2Client createHttp2Client (LifeCycle.Listener lifeCycleListener) throws Exception {
        return createHttp2Client(new QueuedThreadPool(), lifeCycleListener);
    }

    public static HTTP2Client createHttp2Client(final Executor executor, LifeCycle.Listener lifeCycleListener) throws Exception {
        HTTP2Client http2Client = new HTTP2Client();
        http2Client.addLifeCycleListener(lifeCycleListener);
        http2Client.setExecutor(executor);
        http2Client.start();
        return http2Client;
    }

    @Override
    public void connect(IConnectionStatusListener listener) throws Exception {
        apnsClient = createHttp2Client(clientLifeCycleListener);
        Promise<Session> promise = new Promise<Session>() {
            @Override
            public void succeeded(Session session) {
                logger.trace("ApnsHtt2Client connect succeed: {}", name);
                listener.connected(session);
            }
            @Override
            public void failed(Throwable ex) {
                logger.warn("ApnsHtt2Client connect failed: {}", name, ex);
                listener.reconnect();
            }
        };
        Session.Listener sessionListener = new Session.Listener.Adapter() {
            @Override
            public void onReset(Session session, ResetFrame frame) {
                logger.warn("ApnsHtt2Client session onReset: {}", name);
                listener.reconnect();
            }
            @Override
            public void onClose(Session session, GoAwayFrame frame) {
                logger.warn("ApnsHtt2Client session onClose: {}", name);
            }
            @Override
            public boolean onIdleTimeout(Session session) {
                logger.warn("ApnsHtt2Client session onIdleTimeout: {}", name);
                return false; //不关闭连接
            }
            @Override
            public void onFailure(Session session, Throwable failure) {
                logger.warn("ApnsHtt2Client session onFailure: {}", name, failure);
                listener.reconnect();
            }
            @Override
            public void onPing(Session session, PingFrame frame) {
                if (!frame.isReply()) {
                    logger.warn("ApnsHtt2Client session received ping frame: {}", name);
                }
            }
        };
        final SslContextFactory sslContextFactory = new SslContextFactory(true);
        try {
            final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagers, null);
            sslContextFactory.setSslContext(sslContext);
            sslContextFactory.start();
            apnsClient.connect(sslContextFactory, new InetSocketAddress(apnsServerIP, APNS_PORT), sessionListener, promise);
        } catch (Exception ex) {
            promise.failed(ex);
        }
    }

    @Override
    public void push(Session session, PushEvent event, IConnectionStatusListener connListener, IPushStatusListener statusListener) {
        if (event.testEvent) {
            statusListener.onPushSucceed(event, 1);
            return;
        }

        HttpFields requestFields = new HttpFields();
        requestFields.put("apns-id", UUID.randomUUID().toString());
        requestFields.put("apns-expiration", "0");
        requestFields.put("apns-priority", "10");
        requestFields.put("apns-topic", apnsTopic);

        MetaData.Request request = new MetaData.Request("POST", new HttpURI(URI_BASE + event.tokens[0]), HttpVersion.HTTP_2, requestFields);
        HeadersFrame headersFrame = new HeadersFrame(request, null, false);
        Stream.Listener responseListener = new ResponseListener(event,statusListener);
        session.newStream(headersFrame, new Promise<Stream>() {
            @Override
            public void succeeded(Stream stream) {
                logger.trace("push one: eventId={}, topic={},token={},payload={}", event.id, event.topic,event.tokens[0], event.payload);
                ByteBuffer content = ByteBuffer.wrap(event.payload.getBytes(Charset.forName("UTF-8")));
                DataFrame requestContent = new DataFrame(stream.getId(), content, true);
                stream.data(requestContent, new Callback() {
                    //Stream发送状态的的回调
                    public void succeeded() {
                        logger.trace("stream.data Callback.succeed");
                    }
                    public void failed(Throwable ex) {//没有及时调用ping会造成此处EofException
                        logger.debug("stream.data Callback.failed");
                        statusListener.onPushFailed(event, 1);
                        connListener.onFailed();
                    }
                });
            }

            @Override
            public void failed(Throwable ex) {
                logger.debug("stream.newStream failed", ex);
                statusListener.onPushFailed(event, 1);
                connListener.onFailed();
            }
        }, responseListener);
    }

    @Override
    public void ping(Session session, IConnectionStatusListener listener) {
        if (apnsClient != null && session != null) {
            if (!apnsClient.isRunning() || apnsClient.isFailed()) {
                listener.onFailed();
            } else {
                session.ping(new PingFrame(System.currentTimeMillis(), false), new Callback() {
                    public void failed(Throwable ex) {
                        logger.warn("ApnsHtt2Client session ping failed: {}", name, ex);
                        listener.onFailed();
                    }

                    @Override
                    public void succeeded() {
                        listener.onSucceed();
                    }
                });
            }
        }
    }

    @Override
    public boolean isAvailable(Session session) {
        return apnsClient != null && session != null
            && session.getStreams().size() < MAX_STREAM_SIZE
            && apnsClient.isRunning() && !apnsClient.isFailed();
    }

    @Override
    public void close(Session session) {
        if (session != null) {
            session.close(1, null, new Callback() {});
        }
        if (apnsClient != null) {
            try {
                apnsClient.stop();
            } catch (Exception ex) {
                logger.warn("Close apns client failed: {}", name, ex);
            }
            apnsClient = null;
        }
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
    }
}
