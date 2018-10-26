package net.arksea.pusher.server.cast;

import akka.actor.ActorRef;
import net.arksea.pusher.server.service.CastJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2018/2/28.
 */
@Component
public class CastJobManagerState {
    Map<ActorRef, CastJobManager.ProductInfo> jobMap = new HashMap<>();
    Map<String, CastJobManager.ProductInfo> productInfoMap = new HashMap<>();
    @Value("${push.castJobManager.maxJobsPerProduct}")
    int maxJobsPerProduct;
    @Value("${push.castJobManager.pushClientFactoryClass}")
    String pushClientFactoryClass;
    @Autowired
    public CastJobService castJobService;
    @Autowired
    public TargetSourceFactory targetSourceFactory;
    @Autowired
    JobResources jobResources;
}
