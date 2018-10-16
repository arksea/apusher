package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;
import net.arksea.pusher.entity.UserDailyCast;

/**
 *
 * Created by xiaohaixing on 2017/11/7.
 */
public class AddUserDailyCast implements PushRequest<Long> {
    public final UserDailyCast dailyCast;
    public AddUserDailyCast(UserDailyCast dailyCast) {
        this.dailyCast = dailyCast;
    }
}
