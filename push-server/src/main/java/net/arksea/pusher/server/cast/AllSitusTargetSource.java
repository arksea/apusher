package net.arksea.pusher.server.cast;

import akka.dispatch.Futures;
import net.arksea.pusher.entity.CastJob;
import net.arksea.pusher.entity.PushTarget;
import net.arksea.pusher.server.Partition;
import net.arksea.pusher.server.service.PushTargetService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Future;

import java.util.List;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2017/11/10.
 */
class AllSitusTargetSource implements ITargetSource {
    private final static Logger logger = LogManager.getLogger(AllSitusTargetSource.class);
    private final List<String> situsList;
    private final PushTargetService pushTargetService;
    private final int maxPusherCount;

    public AllSitusTargetSource(PushTargetService pushTargetService, List<String> situsList, int maxPusherCount) {
        this.pushTargetService = pushTargetService;
        this.situsList = situsList;
        this.maxPusherCount = maxPusherCount;
    }

    @Override
    public Future<List<PushTarget>> nextPage(CastJob job, Map<String,String> payloadCache) {
        int situsIndex = job.getLastPartition();
        String situs = situsList.get(situsIndex);
        if (situsIndex < situsList.size()) {
            String product = job.getProduct();
            List<PushTarget> targets = pushTargetService.findSitusTop(product, job.getLastUserId(), situs);
            logger.trace("call nextPage(job={}),return {} count PushTarget in situs {}({})",job.getId(), targets.size(), situsIndex, situs);
            return Futures.successful(targets);
        } else {
            logger.trace("call nextPage(job={}), situs {}({}) nomore PushTarget",job.getId(), situsIndex, situs);
            return Futures.successful(null);
        }
    }

    public int getPusherCount(CastJob job) {
        long targetCount = pushTargetService.countByPartitionAndProduct(0, job.getProduct());
        int count =  (int)(targetCount * Partition.MAX_USER_PARTITION / pusherCountConst()) + 1;
        return Math.min(count, maxPusherCount);
    }
}
