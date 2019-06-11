package net.arksea.pusher.server.cast;

import akka.dispatch.Futures;
import net.arksea.pusher.server.Partition;
import net.arksea.pusher.entity.CastJob;
import net.arksea.pusher.entity.PushTarget;
import net.arksea.pusher.server.repository.PushTargetDao;
import net.arksea.pusher.server.service.DailyCastService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Future;

import java.util.List;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2018/2/5.
 */
public class DailyBroadTargetSource implements ITargetSource {
    private final static Logger logger = LogManager.getLogger(DailyBroadTargetSource.class);

    private final DailyCastService service;
    private final PushTargetDao pushTargetDao;
    private final int maxPusherCount;

    public DailyBroadTargetSource(DailyCastService service, PushTargetDao pushTargetDao, int maxPusherCount) {
        this.service = service;
        this.pushTargetDao = pushTargetDao;
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
            long dailyCastId = Long.parseLong(job.getCastTarget());
            return service.findPartitionTop(partition, job.getProduct(), dailyCastId, job.getLastUserId(), payloadCache);
        } else {
            logger.trace("call nextPage(job={}), partition {} nomore PushTarget",job.getId(), partition);
            return Futures.successful(null);
        }
    }

    public int getPusherCount(CastJob job) {
        long targetCount = pushTargetDao.countByPartitionAndProduct(0, job.getProduct());
        int count =  (int)(targetCount * Partition.MAX_USER_PARTITION / pusherCountConst()) + 1;
        return Math.min(count, maxPusherCount);
    }
}
