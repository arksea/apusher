package net.arksea.pusher.server.cast;

import akka.dispatch.Futures;
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
class SitusCastTargetSource implements ITargetSource {
    private final static Logger logger = LogManager.getLogger(SitusCastTargetSource.class);
    private final String situs;
    private final PushTargetService pushTargetService;

    public SitusCastTargetSource(PushTargetService pushTargetService, String situs) {
        this.pushTargetService = pushTargetService;
        this.situs = situs;
    }

    @Override
    public Future<List<PushTarget>> nextPage(CastJob job, Map<String,String> payloadCache) {
        List<PushTarget> targets = pushTargetService.findSitusTop(job.getProduct(), job.getLastUserId(), situs);
        logger.trace("call nextPage(job={}),return {} cout PushTarget",job.getId(), targets.size());
        return Futures.successful(targets);
    }

    public int getPusherCount(CastJob job) {
        return 5;
    }
}
