package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;
import net.arksea.pusher.entity.DailyCast;

/**
 *
 * Created by xiaohaixing on 2017/11/7.
 */
public class AddDailyCast implements PushRequest<Long> {
    public final DailyCast dailyCast;
    public AddDailyCast(DailyCast dailyCast) {
        this.dailyCast = dailyCast;
    }
}
