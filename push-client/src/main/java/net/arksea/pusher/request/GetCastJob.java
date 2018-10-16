package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;
import net.arksea.pusher.entity.CastJob;

/**
 *
 * Created by xiaohaixing on 2017/11/7.
 */
public class GetCastJob implements PushRequest<CastJob> {
    public final long jobId;
    public GetCastJob(long jobId) {
        this.jobId = jobId;
    }
}
