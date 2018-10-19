package net.arksea.pusher.apns;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * Created by xiaohaixing on 2016/8/5.
 */
public class ApnsClientUtils {
    private static final Logger log = LogManager.getLogger(ApnsClientUtils.class);
    private static ObjectMapper objectMapper = new ObjectMapper();
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
            statusListener.onSucceed(event);
            return;
        }
        // Prepare the HTTP request headers.
        HttpFields requestFields = new HttpFields();
        requestFields.put("apns-id", UUID.randomUUID().toString());
        requestFields.put("apns-expiration", "0");
        requestFields.put("apns-priority", "10");
        requestFields.put("apns-topic", apnsTopic);

        // Prepare the HTTP request object.
        MetaData.Request request = new MetaData.Request("POST", new HttpURI(URI_BASE + event.token), HttpVersion.HTTP_2, requestFields);
        // Create the HTTP/2 HEADERS frame representing the HTTP request.
        HeadersFrame headersFrame = new HeadersFrame(request, null, false);
        // Prepare the listener to receive the HTTP response frames.
        Stream.Listener responseListener = new ResponseListener(event,statusListener);
        // Send the HEADERS frame to create a stream.
        session.newStream(headersFrame, new Promise<Stream>() {
            @Override
            public void succeeded(Stream stream) {
                // Use the Stream object to send request content, if any, using a DATA frame.
                log.trace("push one: eventId={}, topic={},token={},payload={}", event.id, event.topic,event.token, event.payload);
                ByteBuffer content = ByteBuffer.wrap(event.payload.getBytes(Charset.forName("UTF-8")));
                DataFrame requestContent = new DataFrame(stream.getId(), content, true);
                stream.data(requestContent, new Callback() {
                    //Stream发送状态的的回调
                    public void succeeded() {
                        log.trace("stream.data Callback.succeed");
                    }

                    public void failed(Throwable ex) {//没有及时调用ping会造成此处EofException
                        log.trace("stream.data Callback.failed");
                        statusListener.onFailed(-1, ex, event);
                    }
                });
            }

            @Override
            public void failed(Throwable ex) {
                log.trace("stream.newStream failed", ex);
                statusListener.onFailed(-1, ex, event);
            }
        }, responseListener);
    }

    static class ResponseListener extends Stream.Listener.Adapter {
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
                    statusListener.onSucceed(event);
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
                log.warn("onData when succeed: {};eventId={}, topic={},token={}", msg,event.id, event.topic,event.token);
                callback.succeeded();
            } else {
                try {
                    Map ret = objectMapper.readValue(body, Map.class);
                    String reason = (String)ret.get("reason");
                    log.trace("apns push failed: {};eventId={},topic={},token={}",msg,event.id,event.topic,event.token);
                    statusListener.onFailed(status.get(), reason, event);
                } catch (IOException ex) {
                    log.error("parse apns resule failed: "+body, ex);
                    statusListener.onFailed(status.get(), ex, event);
                }
                callback.failed(new Exception(msg));
            }

        }
    };


}