package net.arksea.pusher.apns;

import net.arksea.pusher.IPushClient;
import net.arksea.pusher.IPushClientFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http2.api.Session;

import java.io.FileInputStream;
import java.net.InetAddress;
import java.util.Properties;

/**
 *
 * Created by xiaohaixing on 2018/10/26.
 */
public class PushClientFactory implements IPushClientFactory<Session> {
    private static final Logger logger = LogManager.getLogger(PushClientFactory.class);
    private int index;
    private InetAddress[] apnsAddrs;
    public PushClientFactory() {
    }
    @Override
    public IPushClient<Session> create(String name, String productId) throws Exception {
        Properties prop = new Properties();
        prop.load(new FileInputStream("./config/pusher-apns.properties"));
        String pwd = prop.getProperty("product."+productId+".password");
        String apnsTopic = prop.getProperty("product."+productId+".apns-topic");
        String keyFile = "./config/production-"+productId+".p12";
        String apnsAddr = getApnsAddress();
        return new PushClient(name, apnsTopic, apnsAddr, pwd, keyFile);
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
