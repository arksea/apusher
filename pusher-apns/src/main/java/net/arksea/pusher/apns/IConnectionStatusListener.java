package net.arksea.pusher.apns;

/**
 *
 * Created by xiaohaixing on 2018/10/25.
 */
public interface IConnectionStatusListener {
    void onSucceed();
    void onFailed();
}
