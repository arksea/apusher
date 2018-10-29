package net.arksea.pusher.baidu;

import net.arksea.pusher.IPushClient;
import net.arksea.pusher.IPushClientFactory;
import org.apache.commons.lang.StringUtils;

import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 *
 * Created by xiaohaixing on 2018/10/26.
 */
public class PushClientFactory implements IPushClientFactory {
    @Override
    public IPushClient create(String name, String productId) throws Exception {
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
        return new PushClient(apiKey, secretKey, passthroughPayload);
    }
}
