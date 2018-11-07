package net.arksea.pusher.huawei;

import net.arksea.pusher.IPushClient;
import net.arksea.pusher.IPushClientFactory;

import java.io.FileInputStream;
import java.util.Properties;

/**
 *
 * Created by xiaohaixing on 2018/10/26.
 */
public class PushClientFactory implements IPushClientFactory<String> {
    @Override
    public IPushClient<String> create(String name, String productId) throws Exception {
        Properties prop = new Properties();
        prop.load(new FileInputStream("./config/pusher-huawei.properties"));
        String appId = prop.getProperty("product."+productId+".appId");
        String appKey = prop.getProperty("product."+productId+".appKey");
        return new PushClient(appId, appKey);
    }
}
