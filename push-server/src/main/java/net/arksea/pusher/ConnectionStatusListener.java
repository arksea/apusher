package net.arksea.pusher;

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
        pushActor.tell(new ConnectionFailed(), ActorRef.noSender());
    }

    @Override
    public void onSucceed() {
        pushActor.tell(new ConnectionSucceed(), ActorRef.noSender());
    }

    @Override
    public void reconnect() {
        pushActor.tell(new Reconnect(), ActorRef.noSender());
    }

    @Override
    public void connected(Object session) {
        pushActor.tell(new ConnectSucceed(session), ActorRef.noSender());

    }
}
