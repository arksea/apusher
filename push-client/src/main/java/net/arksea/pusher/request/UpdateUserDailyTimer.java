package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;

/**
 * 新建或更新用户每日定时器，
 * 成功返回UserDailyTimer记录ID值（long）
 * 失败返回null
 * Created by xiaohaixing on 2018/2/5.
 */
public class UpdateUserDailyTimer implements PushRequest<Long> {
    public final String product;
    public final String userId;
    public final String payloadType;
    public long timerId;
    public int zoneOffset;
    public final int minuteOfDay;
    public final String payloadUrl;
    public final String payloadCacheKeys;
    public final String days;
    public final boolean enabled;

    public UpdateUserDailyTimer(String product, String userId, String payloadType, long timerId, int zoneOffset, int minuteOfDay,
                                String payloadUrl, String payloadCacheKeys, String days, boolean enabled) {
        super();
        this.product = product;
        this.userId = userId;
        this.payloadType = payloadType;
        this.timerId = timerId;
        this.zoneOffset = zoneOffset;
        this.minuteOfDay = minuteOfDay;
        this.payloadUrl = payloadUrl;
        this.payloadCacheKeys = payloadCacheKeys;
        this.days = days;
        this.enabled = enabled;
    }
}
