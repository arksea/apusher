package net.arksea.pusher.apns;

import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.PushEvent;
import net.arksea.pusher.PushStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.PingFrame;
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
 * Created by xiaohaixing on 2016/8/5.
 */
public class ApnsClientUtils {
    private static final Logger log = LogManager.getLogger(ApnsClientUtils.class);

    private static final int MAX_STREAM_SIZE = 495;
    public static final String APNS_HOST = "api.push.apple.com";
    private static final int APNS_PORT = 443;
    //private static final int APNS_PORT = 2197;
    private static final String URI_BASE = "https://" + APNS_HOST + ":" + APNS_PORT + "/3/device/";

    public static HTTP2Client create (LifeCycle.Listener lifeCycleListener) throws Exception {
        return create(new QueuedThreadPool(), lifeCycleListener);
    }

    public static HTTP2Client create(final Executor executor,  LifeCycle.Listener lifeCycleListener) throws Exception {
        HTTP2Client http2Client = new HTTP2Client();
        http2Client.addLifeCycleListener(lifeCycleListener);
        http2Client.setExecutor(executor);
        http2Client.start();
        return http2Client;
    }

    public static void connect(String apnsServerIP, HTTP2Client client, final KeyManagerFactory keyManagerFactory, Session.Listener sessionListener, Promise<Session> promise)  {
        final SslContextFactory sslContextFactory = new SslContextFactory(true);
        try {
            //init TrustManager
            final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            //init SSLContext
            final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagers, null);
            sslContextFactory.setSslContext(sslContext);
            sslContextFactory.start();
        } catch (Exception ex) {
            promise.failed(ex);
            return;
        }
        client.connect(sslContextFactory, new InetSocketAddress(apnsServerIP, APNS_PORT), sessionListener, promise);
    }

    public static void stop(HTTP2Client client) {
        try {
            client.stop();
        } catch (Exception ex) {
            log.debug("close Http2Client failed", ex);
        }
    }

    public static void ping(Session session, Callback callback) {
        session.ping(new PingFrame(System.currentTimeMillis(), false), callback);
    }

    public static boolean isAvailable(Session session) {
        return session.getStreams().size() < MAX_STREAM_SIZE;
    }

    public static void push(Session session, String apnsTopic, PushEvent event, IPushStatusListener statusListener) {//throws Exception {
        if (event.testEvent) {
            statusListener.onComplete(event, PushStatus.PUSH_SUCCEED);
            return;
        }
        HttpFields requestFields = new HttpFields();
        requestFields.put("apns-id", UUID.randomUUID().toString());
        requestFields.put("apns-expiration", "0");
        requestFields.put("apns-priority", "10");
        requestFields.put("apns-topic", apnsTopic);

        MetaData.Request request = new MetaData.Request("POST", new HttpURI(URI_BASE + event.token), HttpVersion.HTTP_2, requestFields);
        HeadersFrame headersFrame = new HeadersFrame(request, null, false);
        Stream.Listener responseListener = new ResponseListener(event,statusListener);
        session.newStream(headersFrame, new Promise<Stream>() {
            @Override
            public void succeeded(Stream stream) {
                log.trace("push one: eventId={}, topic={},token={},payload={}", event.id, event.topic,event.token, event.payload);
                ByteBuffer content = ByteBuffer.wrap(event.payload.getBytes(Charset.forName("UTF-8")));
                DataFrame requestContent = new DataFrame(stream.getId(), content, true);
                stream.data(requestContent, new Callback() {
                    //Stream发送状态的的回调
                    public void succeeded() {
                        log.trace("stream.data Callback.succeed");
                    }
                    public void failed(Throwable ex) {//没有及时调用ping会造成此处EofException
                        log.debug("stream.data Callback.failed");
                        statusListener.onComplete(event, PushStatus.PUSH_FAILD);
                    }
                });
            }

            @Override
            public void failed(Throwable ex) {
                log.debug("stream.newStream failed", ex);
                statusListener.onComplete(event, PushStatus.PUSH_FAILD);
            }
        }, responseListener);
    }
}