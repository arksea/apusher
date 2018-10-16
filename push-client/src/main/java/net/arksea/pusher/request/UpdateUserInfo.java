package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;

/**
 * 添加或更新用户IOS设备的Token
 * Created by xiaohaixing on 2017/7/6.
 */
public class UpdateUserInfo implements PushRequest<Long> {
    public final String product;
    public final String userId;
    public final String userInfo;

    public UpdateUserInfo(String product, String userId, String userInfo) {
        this.product = product;
        this.userId = userId;
        this.userInfo = userInfo;
    }
}
