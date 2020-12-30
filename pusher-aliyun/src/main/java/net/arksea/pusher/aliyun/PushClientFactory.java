package net.arksea.pusher.aliyun;

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

    @Override
    public IPushClient<String> create(String name, String productId) throws Exception {
        Properties prop = loadProperties();
        String accessKeyId = prop.getProperty("product."+productId+".accessKeyId");
        long appKey = Long.parseLong(prop.getProperty("product."+productId+".appKey"));
        String accessKeySecret = prop.getProperty("product."+productId+".accessKeySecret");
        String regionCfg = prop.getProperty("product."+productId+".region");
        String region = regionCfg == null ? "cn-hangzhou" : regionCfg;
        return new PushClient(accessKeyId, accessKeySecret, appKey, region);
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
        prop.load(new FileInputStream("./config/pusher-aliyun.properties"));
        return prop;
    }
}
