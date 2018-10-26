package net.arksea.pusher.apns;

import akka.actor.ActorRef;

/**
 *
 * Created by xiaohaixing on 2018/10/25.
 */
public class ConnectionStatusListener implements IConnectionStatusListener {
    private final ActorRef pushActor;

    public ConnectionStatusListener(ActorRef pushActor) {
        this.pushActor = pushActor;
    }

    @Override
    public void onFailed() {
        pushActor.tell(new ApnsPushActor.ConnectionFailed(), ActorRef.noSender());
    }

    @Override
    public void onSucceed() {
        pushActor.tell(new ApnsPushActor.ConnectionSucceed(), ActorRef.noSender());
    }
}
