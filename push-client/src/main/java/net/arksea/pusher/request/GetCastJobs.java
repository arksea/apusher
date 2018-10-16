package net.arksea.pusher.request;

import net.arksea.pusher.PushRequest;
import net.arksea.pusher.entity.CastJob;

import java.util.List;

/**
 *
 * Created by xiaohaixing on 2017/11/7.
 */
public class GetCastJobs implements PushRequest<List<CastJob>> {
    public final String payloadType;
    public final int page;
    public final int pageSize;

    public GetCastJobs(int page, int pageSize) {
        this.payloadType = null;
        this.page = page;
        this.pageSize = pageSize;
    }
    public GetCastJobs(String payloadType, int page, int pageSize) {
        this.payloadType = payloadType;
        this.page = page;
        this.pageSize = pageSize;
    }
}
