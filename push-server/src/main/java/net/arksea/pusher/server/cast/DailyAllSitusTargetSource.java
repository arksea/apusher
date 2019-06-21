package net.arksea.pusher.server.cast;

import akka.dispatch.Futures;
import net.arksea.pusher.entity.CastJob;
import net.arksea.pusher.entity.PushTarget;
import net.arksea.pusher.server.Partition;
import net.arksea.pusher.server.repository.PushTargetDao;
import net.arksea.pusher.server.service.DailyCastService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Future;

import java.util.List;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2017/11/10.
 */
class DailyAllSitusTargetSource implements ITargetSource {
    private final static Logger logger = LogManager.getLogger(DailyAllSitusTargetSource.class);
    private final List<String> situsList;
    private final DailyCastService service;
    private final PushTargetDao pushTargetDao;
    private final int maxPusherCount;

    public DailyAllSitusTargetSource(DailyCastService service, List<String> situsList, PushTargetDao pushTargetDao, int maxPusherCount) {
        this.service = service;
        this.pushTargetDao = pushTargetDao;
        this.situsList = situsList;
        this.maxPusherCount = maxPusherCount;
    }

    @Override
    public int maxPartition() {
        return situsList.size();
    }

    @Override
    public Future<List<PushTarget>> nextPage(CastJob job, Map<String,String> payloadCache) {
        int situsIndex = job.getLastPartition();
        String situs = situsList.get(situsIndex);
        if (situsIndex < situsList.size()) {
            String product = job.getProduct();
            long dailyCastId = Long.parseLong(job.getCastTarget());
            return service.findSitusTop(situs, job.getProduct(), dailyCastId, job.getLastUserId(), payloadCache);
        } else {
            logger.trace("call nextPage(job={}), situs {}({}) nomore PushTarget",job.getId(), situsIndex, situs);
            return Futures.successful(null);
        }
    }

    public int getPusherCount(CastJob job) {
        long targetCount = pushTargetDao.countByPartitionAndProduct(0, job.getProduct());
        int count =  (int)(targetCount * Partition.MAX_USER_PARTITION / pusherCountConst()) + 1;
        return Math.min(count, maxPusherCount);
    }
}
