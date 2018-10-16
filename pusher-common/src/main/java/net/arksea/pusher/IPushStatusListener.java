package net.arksea.pusher;

/**
 *
 * Created by xiaohaixing_dian91 on 2018/03/06.
 */
public interface IPushStatusListener {
    void onSucceed(final PushEvent event);
    void onFailed(final int status, final Object reason, final PushEvent event);
}