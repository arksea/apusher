package net.arksea.pusher.server;

import net.arksea.pusher.server.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * Created by xiaohaixing on 2017/7/6.
 */
@Component
public class PushServerStat {
    @Autowired
    CastJobService castJobService;
    @Autowired
    PushTargetService pushTargetService;
    @Autowired
    UserDailyTimerService userDailyTimerService;
    @Autowired
    UserDailyCastService userDailyCastService;
    @Autowired
    DailyCastService dailyCastService;
}
