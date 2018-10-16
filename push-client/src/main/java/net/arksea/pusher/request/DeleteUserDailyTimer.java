package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;

/**
 * 更新用户每日定时器状态，
 * 成功返回true，失败返回false
 * Created by xiaohaixing on 2018/2/5.
 */
public class DeleteUserDailyTimer implements PushRequest<Boolean> {
    public final String product;
    public final String userId;
    public final String payloadType;
    public final long timerId;

    public DeleteUserDailyTimer(String product, String userId, String payloadType, long timerId) {
        super();
        this.product = product;
        this.userId = userId;
        this.payloadType = payloadType;
        this.timerId = timerId;
    }
}
