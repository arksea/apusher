package net.arksea.pusher.apns;

import net.arksea.pusher.IPushClient;
import net.arksea.pusher.IPushClientFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.io.FileInputStream;
import java.net.InetAddress;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 *
 * Created by xiaohaixing on 2018/10/26.
 */
public class PushClientFactory implements IPushClientFactory<Session> {
    private static final Logger logger = LogManager.getLogger(PushClientFactory.class);
    private int index;
    private InetAddress[] apnsAddrs;
    private QueuedThreadPool queuedThreadPool;
    private HTTP2Client apnsClient;

    public PushClientFactory() {
        try (FileInputStream in = new FileInputStream("./config/pusher-apns.properties")){
            Properties prop = new Properties();
            prop.load(in);
            String maxStr = prop.getProperty("httpClient.threadPool.maxCount", "10");
            int max = Integer.parseInt(maxStr);
            String minStr = prop.getProperty("httpClient.threadPool.minCount", "1");
            int min = Integer.parseInt(minStr);
            queuedThreadPool = new QueuedThreadPool(max, min);
            queuedThreadPool.setName("push-client");
            queuedThreadPool.setDaemon(true);
            queuedThreadPool.start();
            //HTTP2Client中的SelecterManager不能正确关闭，造成长期运行的进程出现越来越多的无用selectoer线程，所以不能每次任务够创建一个实例
            apnsClient = createHttp2Client(queuedThreadPool);

        } catch (Exception ex) {
            throw new RuntimeException("init PushClientFactory failed", ex);
        }
    }

    public static HTTP2Client createHttp2Client(final Executor executor) throws Exception {
        HTTP2Client http2Client = new HTTP2Client();
        //http2Client.addLifeCycleListener(lifeCycleListener);
        http2Client.setExecutor(executor);
        http2Client.start();
        return http2Client;
    }

    @Override
    public IPushClient<Session> create(String name, String productId) throws Exception {
        try (FileInputStream in = new FileInputStream("./config/pusher-apns.properties")) {
            Properties prop = new Properties();
            prop.load(in);
            String pwd = prop.getProperty("product." + productId + ".password");
            String apnsTopic = prop.getProperty("product." + productId + ".apns-topic");
            String keyFile = "./config/production-" + productId + ".p12";
            String apnsAddr = getApnsAddress();
            return new PushClient(name, apnsTopic, apnsAddr, pwd, keyFile, apnsClient);
        }
    }

    private synchronized String getApnsAddress() throws Exception {
        if (index < 1) {
            try {
                apnsAddrs = InetAddress.getAllByName(PushClient.APNS_HOST);
            } catch (Exception ex) {
                if (apnsAddrs == null) {
                    throw ex;
                } else {
                    logger.warn("Update host address list failed, use old list(size={}).", apnsAddrs.length, ex);
                }
            }
            index = apnsAddrs.length - 1;
            logger.debug("APNS Server address count = " + apnsAddrs.length);
        } else {
            --index;
        }
        return apnsAddrs[index].getHostAddress();
    }
}
