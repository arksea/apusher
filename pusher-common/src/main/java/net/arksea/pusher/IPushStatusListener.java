package net.arksea.pusher;

/**
 *
 * Created by xiaohaixing_dian91 on 2018/03/06.
 */
public interface IPushStatusListener {
    void onComplete(PushEvent event, PushStatus status);
}