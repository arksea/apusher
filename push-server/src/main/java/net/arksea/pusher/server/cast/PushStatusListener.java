package net.arksea.pusher.server.cast;

import akka.actor.ActorRef;
import net.arksea.pusher.PushEvent;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.PushStatus;

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
    public void onComplete(PushEvent event, PushStatus status) {
        switch (status) {
            case PUSH_FAILD:
                jobActor.tell(new CastJobActor.PushFailed(event), ActorRef.noSender());
                break;
            case INVALID_TOKEN:
                jobActor.tell(new CastJobActor.PushInvalid(event), ActorRef.noSender());
                this.beans.pushTargetService.updateTokenStatus(event.tokens[0], false);
                break;
            case PUSH_SUCCEED:
            default:
                jobActor.tell(new CastJobActor.PushSucceed(event), ActorRef.noSender());
                break;
        }
    }
}
