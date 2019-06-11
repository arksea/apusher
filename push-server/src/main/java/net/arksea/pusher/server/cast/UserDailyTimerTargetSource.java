package net.arksea.pusher.server.cast;

import akka.dispatch.Futures;
import net.arksea.pusher.server.Partition;
import net.arksea.pusher.entity.CastJob;
import net.arksea.pusher.entity.PushTarget;
import net.arksea.pusher.server.service.UserDailyTimerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Future;

import java.util.List;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2018/2/5.
 */
public class UserDailyTimerTargetSource implements ITargetSource {
    private final static Logger logger = LogManager.getLogger(UserDailyTimerTargetSource.class);
    private final UserDailyTimerService service;
    private final int maxPusherCount;
    public UserDailyTimerTargetSource(UserDailyTimerService service,int maxPusherCount) {
        this.service = service;
        this.maxPusherCount = maxPusherCount;
    }

    @Override
    public int maxPartition() {
        return Partition.MAX_USER_PARTITION;
    }

    @Override
    public Future<List<PushTarget>> nextPage(CastJob job, Map<String,String> payloadCache) {
        int partition = job.getLastPartition();
        int minuteOfDay = Integer.parseInt(job.getCastTarget());
        if (hasPushTargets(job)) {
            return service.findPartitionTop(partition, job.getProduct(), minuteOfDay, job.getPayloadType(), job.getLastUserId(), payloadCache);
        } else {
            logger.trace("call nextPage(job={}), partition {} nomore PushTarget",job.getId(), partition);
            return Futures.successful(null);
        }
    }

    @Override
    public boolean hasPushTargets(CastJob job) {
        int partition = job.getLastPartition();
        int minuteOfDay = Integer.parseInt(job.getCastTarget());
        if (partition == 0) { //只需首次判断即可
            if (service.existsTimer(job.getProduct(),minuteOfDay,job.getPayloadType())) {
                return true;
            }
        } else if (partition < Partition.MAX_USER_PARTITION) {
            return true;
        }
        return false;
    }

    @Override
    public int getPusherCount(CastJob job) {
        int minuteOfDay = Integer.parseInt(job.getCastTarget());
        long timerCount = service.getTimerCount(job.getProduct(), minuteOfDay, job.getPayloadType());
        int count =  (int)(timerCount / pusherCountConst()) + 1;
        return Math.min(count, maxPusherCount);
    }
}
