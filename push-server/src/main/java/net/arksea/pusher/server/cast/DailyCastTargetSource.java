package net.arksea.pusher.server.cast;

import akka.dispatch.Futures;
import net.arksea.pusher.server.Partition;
import net.arksea.pusher.entity.CastJob;
import net.arksea.pusher.entity.PushTarget;
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
public class DailyCastTargetSource implements ITargetSource {
    private final static Logger logger = LogManager.getLogger(DailyCastTargetSource.class);

    private final DailyCastService service;
    private final int maxPusherCount;

    public DailyCastTargetSource(DailyCastService service,int maxPusherCount) {
        this.service = service;
        this.maxPusherCount = maxPusherCount;
    }

    @Override
    public Future<List<PushTarget>> nextPage(CastJob job, Map<String,String> payloadCache) {
        int partition = job.getLastPartition();
        if (partition < Partition.MAX_USER_PARTITION) {
            long dailyCastId = Long.parseLong(job.getCastTarget());
            return service.findPartitionTop(partition, job.getProduct(), dailyCastId, job.getLastUserId(), payloadCache);
        } else {
            logger.trace("call nextPage(job={}), partition {} nomore PushTarget",job.getId(), partition);
            return Futures.successful(null);
        }
    }

    public int getPusherCount(CastJob job) {
        return maxPusherCount;
    }
}
