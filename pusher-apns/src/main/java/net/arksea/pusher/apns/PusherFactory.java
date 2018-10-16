package net.arksea.pusher.apns;

import akka.actor.ActorRefFactory;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.IPusher;
import net.arksea.pusher.IPusherFactory;
import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Properties;

/**
 *
 * Created by xiaohaixing on 2018/9/20.
 */
public class PusherFactory implements IPusherFactory {
    @Override
    public IPusher create(String productId, String pusherName,
                          int clientCount, ActorRefFactory actorRefFactory,
                          IPushStatusListener pushStatusListener) throws Exception {
        Properties prop = new Properties();
        prop.load(new FileInputStream("./config/pusher-apns.properties"));
        String pwd = prop.getProperty("product."+productId+".password");
        String apnsTopic = prop.getProperty("product."+productId+".apns-topic");
        final InputStream keyIn = new FileInputStream("./config/production-"+productId+".p12");
        final char[] pwdChars = pwd.toCharArray();
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(keyIn, pwdChars);
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(keyStore, pwdChars);
        return new ApnsPusher(pusherName, clientCount, apnsTopic, actorRefFactory,keyManagerFactory, pushStatusListener);
    }
}
