package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;

/**
 *
 * Created by xiaohaixing on 2017/11/7.
 */
public class DeleteUserDailyCast implements PushRequest<Boolean> {
    public final String product;
    public final String payloadType;

    public DeleteUserDailyCast(String product, String payloadType) {
        this.product = product;
        this.payloadType = payloadType;
    }
}
