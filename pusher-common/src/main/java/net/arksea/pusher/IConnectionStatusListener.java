package net.arksea.pusher;

/**
 *
 * Created by xiaohaixing on 2018/10/25.
 */
public interface IConnectionStatusListener {
    void onSucceed();
    void onFailed();
    void reconnect();
    void connected(Object session);
}
