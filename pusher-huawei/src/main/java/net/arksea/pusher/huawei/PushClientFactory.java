package net.arksea.pusher.huawei;

import net.arksea.pusher.IPushClient;
import net.arksea.pusher.IPushClientFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * Created by xiaohaixing on 2018/10/26.
 */
public class PushClientFactory implements IPushClientFactory<String> {
    private static final Logger logger = LogManager.getLogger(PushClientFactory.class);
    private int index;
    @Override
    public IPushClient<String> create(String name, String productId) throws Exception {
        return null;
    }
}
