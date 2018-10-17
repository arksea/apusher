package net.arksea.pusher.server.cast;

import net.arksea.pusher.entity.CastJob;
import net.arksea.pusher.entity.PushTarget;
import scala.concurrent.Future;

import java.util.List;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2017/11/10.
 */
public interface ITargetSource {
    default int pusherCountConst() {
        return 100000;
    }
    Future<List<PushTarget>> nextPage(CastJob job, Map<String,String> payloadCache);
    default boolean hasPushTargets(CastJob job) {
        return true;
    }
    int getPusherCount(CastJob job);
}
