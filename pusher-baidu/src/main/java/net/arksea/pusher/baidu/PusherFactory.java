package net.arksea.pusher.baidu;

import akka.actor.ActorRefFactory;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.IPusher;
import net.arksea.pusher.IPusherFactory;
import org.apache.commons.lang.StringUtils;

import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

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
        prop.load(new FileInputStream("./config/pusher-baidu.properties"));
        String apiKey = prop.getProperty("product."+productId+".apiKey");
        String secretKey = prop.getProperty("product."+productId+".secretKey");
        Set<String> passthroughPayload = new HashSet<>();
        String str = prop.getProperty("passthroughPayload");
        if (StringUtils.isNotBlank(str)) {
            String[] types = StringUtils.split(str, ',');
            for (String t : types) {
                passthroughPayload.add(t);
            }
        }
        return new BaiduPusher(pusherName, clientCount, actorRefFactory,apiKey, secretKey, passthroughPayload, pushStatusListener);
    }
}
