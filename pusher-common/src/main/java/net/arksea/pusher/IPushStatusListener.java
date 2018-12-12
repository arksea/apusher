package net.arksea.pusher;

/**
 *
 * Created by xiaohaixing_dian91 on 2018/03/06.
 */
public interface IPushStatusListener {
    void onPushSucceed(PushEvent event, int succeedCount);
    void onPushFailed(PushEvent event, int failedCount);
    void onRateLimit(PushEvent event);
    void handleInvalidToken(String token);
}