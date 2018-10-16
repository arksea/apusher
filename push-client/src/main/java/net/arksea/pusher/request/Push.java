package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;

/**
 *
 * Created by xiaohaixing on 2017/11/8.
 */
public class Push implements PushRequest {
    public final String product;
    public final String userId;
    public final String payload;
    public final long expiredTime;

    public Push(String product, String userId, String payload, long expiredTime) {
        this.product = product;
        this.userId = userId;
        this.payload = payload;
        this.expiredTime = expiredTime;
    }
}
