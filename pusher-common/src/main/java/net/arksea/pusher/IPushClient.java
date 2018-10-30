package net.arksea.pusher;

/**
 *
 * Created by xiaohaixing on 2018/10/26.
 */
public interface IPushClient<T> {
    void connect(IConnectionStatusListener listener) throws Exception;
    void push(T session, PushEvent event, IConnectionStatusListener connListener,IPushStatusListener statusListener);
    void ping(T session, IConnectionStatusListener listener);
    boolean isAvailable(T session);
    void close(T session);
}
