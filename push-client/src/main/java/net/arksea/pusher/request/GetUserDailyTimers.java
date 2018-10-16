package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;
import net.arksea.pusher.entity.UserDailyTimer;

import java.util.List;

/**
 *
 * Created by xiaohaixing on 2017/11/7.
 */
public class GetUserDailyTimers implements PushRequest<List<UserDailyTimer>> {
    public final String userId;
    public final String product;

    public GetUserDailyTimers(String userId, String product) {
        this.userId = userId;
        this.product = product;
    }
}
