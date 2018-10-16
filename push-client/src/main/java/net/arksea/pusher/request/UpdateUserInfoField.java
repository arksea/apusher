package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;

/**
 * 添加或更新用户IOS设备的Token
 * Created by xiaohaixing on 2017/7/6.
 */
public class UpdateUserInfoField implements PushRequest {
    public final String product;
    public final String userId;
    public final String name;
    public final Object value;

    public UpdateUserInfoField(String product, String userId, String name, Object value) {
        this.product = product;
        this.userId = userId;
        this.name = name;
        this.value = value;
    }
}
