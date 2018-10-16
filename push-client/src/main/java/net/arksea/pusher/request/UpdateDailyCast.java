package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;
import net.arksea.pusher.entity.DailyCast;

/**
 *
 * Created by xiaohaixing on 2017/11/7.
 */
public class UpdateDailyCast implements PushRequest<Boolean> {
    public final DailyCast dailyCast;
    public UpdateDailyCast(DailyCast dailyCast) {
        this.dailyCast = dailyCast;
    }
}
