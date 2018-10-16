package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;
import net.arksea.pusher.entity.CastJob;

/**
 *
 * Created by xiaohaixing on 2017/11/7.
 */
public class AddCastJob implements PushRequest<Long> {
    public final CastJob job;
    public AddCastJob(CastJob job) {
        this.job = job;
    }
}
