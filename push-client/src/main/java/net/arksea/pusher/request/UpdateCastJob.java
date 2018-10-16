package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;
import net.arksea.pusher.entity.CastJob;

/**
 *
 * Created by xiaohaixing on 2017/11/7.
 */
public class UpdateCastJob implements PushRequest<Boolean> {
    public final CastJob job;
    public UpdateCastJob(CastJob job) {
        this.job = job;
    }
}
