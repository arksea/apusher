package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;

/**
 *
 * Created by xiaohaixing on 2017/11/7.
 */
public class DeleteCastJob implements PushRequest<Boolean> {
    public final long jobId;
    public DeleteCastJob(long jobId) {
        this.jobId = jobId;
    }
}
