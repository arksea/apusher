package net.arksea.pusher.xinge;

import net.arksea.pusher.IPushClient;
import net.arksea.pusher.IPushClientFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 *
 * Created by xiaohaixing on 2019/06/21.
 */
public class PushClientFactory implements IPushClientFactory<String> {
    public PushClientFactory() throws Exception {

    }
    @Override
    public IPushClient<String> create(String name, String productId) throws Exception {
        Properties prop = loadProperties();
        String appId = prop.getProperty("product."+productId+".appId");
        String appKey = prop.getProperty("product."+productId+".appKey");
        String token = prop.getProperty("product."+productId+".postmanToken");
        return new PushClient(appId, appKey, token);
    }

    public int batchPushCount() {
        try {
            Properties prop = loadProperties();
            String str = prop.getProperty("push.batchCount");
            if (str == null) {
                return 100;
            } else {
                return Integer.parseInt(str);
            }
        } catch (Exception ex) {
            return 100;
        }
    }

    private Properties loadProperties() throws IOException {
        Properties prop = new Properties();
        prop.load(new FileInputStream("./config/pusher-xinge.properties"));
        return prop;
    }
}
