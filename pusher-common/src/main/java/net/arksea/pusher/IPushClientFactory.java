package net.arksea.pusher;
/**
 *
 * Created by xiaohaixing on 2018/10/26.
 */
public interface IPushClientFactory<T> {
    IPushClient<T> create(String name, String productId) throws Exception;
}
