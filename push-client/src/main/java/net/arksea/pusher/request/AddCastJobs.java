package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;
import net.arksea.pusher.entity.CastJob;

import java.util.List;

/**
 *
 * Created by xiaohaixing on 2017/11/7.
 */
public class AddCastJobs implements PushRequest<Integer> {
    public final List<CastJob> jobs;
    public AddCastJobs(List<CastJob> jobs) {
        this.jobs = jobs;
    }
}
