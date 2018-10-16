package net.arksea.pusher;

import scala.concurrent.Future;

/**
 *
 * Created by xiaohaixing on 2018/9/12.
 */
public interface IPusher {
    Future<Boolean> push(PushEvent event);
}
