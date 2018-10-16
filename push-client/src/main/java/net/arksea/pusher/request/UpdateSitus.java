package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;

/**
 * 添加或更新用户的定位信息
 * Created by xiaohaixing on 2017/7/6.
 */
public class UpdateSitus implements PushRequest<Long> {
    public final String product;
    public final String userId;
    public final String situs;
    public final String situsGroup;
    public final String location;
    public final String userInfo;

    public UpdateSitus(String product, String userId, String situs, String situsGroup,String location, String userInfo) {
        this.product = product;
        this.userId = userId;
        this.situs = situs;
        this.situsGroup = situsGroup;
        this.location = location;
        this.userInfo = userInfo;
    }
}
