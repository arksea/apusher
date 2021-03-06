package net.arksea.pusher.apns;

import net.arksea.pusher.IConnectionStatusListener;
import net.arksea.pusher.IPushClient;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.PushEvent;
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
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.util.UUID;

/**
 *
 * Created by xiaohaixing on 2018/10/26.
 */
public class PushClient implements IPushClient<Session> {
    private final static Logger logger = LogManager.getLogger(PushClient.class);
    private static final int MAX_STREAM_SIZE = 495; //apns每个session最多允许开启500个Stream，这里预留少量stream给ping使用
    public static final String APNS_HOST = "api.push.apple.com";
    private static final int APNS_PORT = 443;
    private static final int APNS_DEV_PORT = 2197;
    private static final String URI_BASE = "https://" + APNS_HOST + ":" + APNS_PORT + "/3/device/";

    final private String name;
    final private String apnsServerIP;
    final private String keyPassword;
    final public String keyFile;
    final private String apnsTopic;
    private final HTTP2Client apnsClient;
    private int connectCount;

    public PushClient(String name, String apnsTopic, String apnsServerIP, String password, String keyFile, HTTP2Client apnsClient) {
        this.name = name;
        this.apnsTopic = apnsTopic;
        this.apnsServerIP = apnsServerIP;
        this.keyPassword = password;
        this.keyFile = keyFile;
        this.apnsClient = apnsClient;
    }

    @Override
    public void connect(IConnectionStatusListener listener) throws Exception {
        ++connectCount;
        Promise<Session> promise = new Promise<Session>() {
            @Override
            public void succeeded(Session session) {
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
                return true; //关闭连接
            }
            @Override
            public void onFailure(Session session, Throwable failure) {
                if (!session.isClosed()) {
                    logger.warn("ApnsHtt2Client session onFailure: {}", name, failure);
                    listener.reconnect();
                }
            }
            @Override
            public void onPing(Session session, PingFrame frame) {
                if (!frame.isReply()) {
                    logger.warn("ApnsHtt2Client session received ping frame: {}", name);
                }
            }
        };
        final SslContextFactory sslContextFactory = new SslContextFactory(true);
        try (final InputStream keyIn = new FileInputStream(keyFile)) {
            final char[] pwdChars = keyPassword.toCharArray();
            final KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(keyIn, pwdChars);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(keyStore, pwdChars);
            final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagers, null);
            sslContextFactory.setSslContext(sslContext);
            sslContextFactory.start();
            String addr = connectCount > 3 ? APNS_HOST : apnsServerIP;
            apnsClient.connect(sslContextFactory, new InetSocketAddress(addr, APNS_PORT), sessionListener, promise);
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
        if (session != null) {
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
            && session.getStreams().size() < MAX_STREAM_SIZE;
    }

    @Override
    public void close(Session session) {
        if (session != null) {
            session.close(0, null, new Callback() {
                public void failed(Throwable ex) {
                    logger.warn("Close session failed: {}", name, ex);
                }
            });
        }
    }
}
