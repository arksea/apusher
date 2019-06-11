package net.arksea.pusher.server.cast;

import akka.dispatch.Futures;
import net.arksea.pusher.server.Partition;
import net.arksea.pusher.entity.CastJob;
import net.arksea.pusher.entity.PushTarget;
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
class SitusGroupCastTargetSource implements ITargetSource {
    private final static Logger logger = LogManager.getLogger(SitusGroupCastTargetSource.class);
    private final List<String> situsGroups;
    private final PushTargetService pushTargetService;
    private final int maxPusherCount;

    public SitusGroupCastTargetSource(PushTargetService pushTargetService, List<String> situsGroups, int maxPusherCount) {
        this.pushTargetService = pushTargetService;
        this.situsGroups = situsGroups;
        this.maxPusherCount = maxPusherCount;
    }

    @Override
    public int maxPartition() {
        return Partition.MAX_USER_PARTITION;
    }

    @Override
    public Future<List<PushTarget>> nextPage(CastJob job, Map<String,String> payloadCache) {
        int partition = job.getLastPartition();
        if (partition < maxPartition()) {
            List<PushTarget> targets = pushTargetService.findSitusGroupTop(partition, job.getProduct(), job.getLastUserId(), situsGroups);
            logger.trace("call nextPage(job={}),return {} cout PushTarget in partition {}",job.getId(), targets.size(), partition);
            return Futures.successful(targets);
        } else {
            logger.trace("call nextPage(job={}), partition {} nomore PushTarget",job.getId(), partition);
            return Futures.successful(null);
        }
    }

    public int getPusherCount(CastJob job) {
        long targetCount = pushTargetService.countByPartitionAndProduct(0, job.getProduct());
        int count =  (int)(targetCount * Partition.MAX_USER_PARTITION / pusherCountConst()) + 1;
        return Math.min(count, maxPusherCount);
    }
}
