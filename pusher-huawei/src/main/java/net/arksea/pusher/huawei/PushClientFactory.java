package net.arksea.pusher.huawei;

import net.arksea.pusher.IPushClient;
import net.arksea.pusher.IPushClientFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 *
 * Created by xiaohaixing on 2018/10/26.
 */
public class PushClientFactory implements IPushClientFactory<String> {
    public PushClientFactory() throws Exception {

    }
    @Override
    public IPushClient<String> create(String name, String productId) throws Exception {
        Properties prop = new Properties();
        prop.load(new FileInputStream("./config/pusher-huawei.properties"));
        String appId = prop.getProperty("product."+productId+".appId");
        String appKey = prop.getProperty("product."+productId+".appKey");
        return new PushClient(appId, appKey);
    }

    public int batchPushCount() {
        try {
            Properties prop = loadProperties();
            prop.load(new FileInputStream("./config/pusher-huawei.properties"));
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
        prop.load(new FileInputStream("./config/pusher-huawei.properties"));
        return prop;
    }
}
