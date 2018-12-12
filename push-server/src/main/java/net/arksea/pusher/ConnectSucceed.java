package net.arksea.pusher;

/**
 *
 * Created by xiaohaixing on 2018/10/26.
 */
public class ConnectSucceed<T> {
    public final T session;

    public ConnectSucceed(T session) {
        this.session = session;
    }
}
