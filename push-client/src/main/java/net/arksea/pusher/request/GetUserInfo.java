package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;

/**
 * Create by xiaohaixing on 2020/3/11
 */
public class GetUserInfo implements PushRequest<String> {
    public final String product;
    public final String userId;

    public GetUserInfo(String product, String userId) {
        this.product = product;
        this.userId = userId;
    }
}
