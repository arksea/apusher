package net.arksea.pusher.server.cast;

import akka.actor.ActorRef;
import net.arksea.pusher.PushEvent;
import net.arksea.pusher.IPushStatusListener;

/**
 *
 * Created by xiaohaixing on 2017/12/13.
 */
public class PushStatusListener  implements IPushStatusListener {
    private final ActorRef jobActor;
    private final JobResources beans;
    public PushStatusListener(ActorRef jobActor, JobResources beans) {
        this.jobActor = jobActor;
        this.beans = beans;
    }
    @Override
    public void onPushSucceed(PushEvent event, int succeedCount) {
        jobActor.tell(new CastJobActor.PushSucceed(event, succeedCount), ActorRef.noSender());
    }
    @Override
    public void onPushFailed(PushEvent event, int failedCount) {
        jobActor.tell(new CastJobActor.PushFailed(event, failedCount), ActorRef.noSender());
    }

    @Override
    public void onRateLimit(PushEvent event) {
        jobActor.tell(new CastJobActor.PushRateLimit(event), ActorRef.noSender());
    }

    @Override
    public void handleInvalidToken(String token) {
        this.beans.pushTargetService.updateTokenStatus(token, false);
    }
}
