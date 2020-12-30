package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;

/**
 * 设置用户IOS设备Token的活跃状态，推送服务在APNS返回状态为unactived时会被设置成false，
 * 调用者也可以用此接口主动将其设置为false,当一个token的active状态为false时将不会对其推送消息
 * Created by xiaohaixing on 2017/7/6.
 */
public class UpdateTokenStatusByUID implements PushRequest<Boolean> {
    public final String userId;
    public final boolean actived;

    public UpdateTokenStatusByUID(final String userId, final boolean actived) {
        this.userId = userId;
        this.actived = actived;
    }
}
