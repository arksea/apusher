package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;
import net.arksea.pusher.entity.UserDailyCast;

/**
 *
 * Created by xiaohaixing on 2017/11/7.
 */
public class UpdateUserDailyCast implements PushRequest<Boolean> {
    public final UserDailyCast dailyCast;
    public UpdateUserDailyCast(UserDailyCast dailyCast) {
        this.dailyCast = dailyCast;
    }
}
