package net.arksea.pusher.server.cast;

import net.arksea.pusher.server.service.CastJobService;
import net.arksea.pusher.server.service.DailyCastService;
import net.arksea.pusher.server.service.UserDailyCastService;
import net.arksea.pusher.server.service.PushTargetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * Created by xiaohaixing on 2017/7/6.
 */
@Component
public class JobResources {
    @Autowired
    public CastJobService castJobService;
    @Autowired
    public PushTargetService pushTargetService;

    @Autowired
    public UserDailyCastService userDailyCastService;

    @Autowired
    public DailyCastService dailyCastService;
}
