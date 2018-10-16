package net.arksea.pusher.server.cast;

import akka.dispatch.Futures;
import net.arksea.pusher.server.Partition;
import net.arksea.pusher.entity.CastJob;
import net.arksea.pusher.entity.PushTarget;
import net.arksea.pusher.server.service.PushTargetService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Future;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * 推送目标源：指定User列表
 * Created by xiaohaixing on 2017/11/10.
 */
public class SpecifiedTargetSource implements ITargetSource {
    private final static Logger logger = LogManager.getLogger(SpecifiedTargetSource.class);
    private final PushTargetService pushTargetService;
    private final List<String> users;

    public SpecifiedTargetSource(PushTargetService pushTargetService, List<String> users) {
        this.pushTargetService = pushTargetService;
        this.users = users;
    }

    @Override
    public Future<List<PushTarget>> nextPage(CastJob job, Map<String,String> payloadCache) {
        List<PushTarget> targets = new LinkedList<>();
        if (job.getLastPartition() == 0) {
            int index = 0;
            if (job.getLastUserId() != null) {
                index = users.indexOf(job.getLastUserId()) + 1;
            }

            ListIterator<String> it = users.listIterator(index);

            while (it.hasNext()) {
                String userId = it.next();
                List<PushTarget> ret = pushTargetService.findByProductAndUserId(job.getProduct(), userId);
                if (ret.isEmpty()) {
                    logger.warn("The Job({}) specified user not exists: {}", job.getId(), userId);
                } else {
                    targets.add(ret.get(0));
                }
            }
            job.setLastPartition(Partition.MAX_USER_PARTITION - 1);
        }
        logger.trace("call nextPage(job={}),return {} cout PushTarget",job.getId(), targets.size());
        return Futures.successful(targets);
    }

    public int getPusherCount(CastJob job) {
        return 1;
    }
}
