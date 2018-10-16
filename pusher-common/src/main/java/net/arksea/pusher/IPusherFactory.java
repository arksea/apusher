package net.arksea.pusher;

import akka.actor.ActorRefFactory;

/**
 *
 * Created by xiaohaixing on 2018/9/20.
 */
public interface IPusherFactory {
    IPusher create(String productId, String pusherName, int clientCount,
                   ActorRefFactory actorRefFactory, IPushStatusListener pushStatusListener) throws Exception;
}
