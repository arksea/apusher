package net.arksea.pusher.server.cast;

import akka.actor.ActorSystem;
import net.arksea.pusher.entity.CastJob;
import net.arksea.pusher.server.repository.PushTargetDao;
import net.arksea.pusher.server.service.DailyCastService;
import net.arksea.pusher.server.service.PushTargetService;
import net.arksea.pusher.server.service.UserDailyTimerService;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 *
 * Created by xiaohaixing on 2017/11/10.
 */
@Component
public class TargetSourceFactory {
    private final static Logger logger = LogManager.getLogger(TargetSourceFactory.class);

    @Autowired
    PushTargetService pushTargetService;
    @Autowired
    UserDailyTimerService userDailyTimerService;
    @Autowired
    PushTargetDao pushTargetDao;
    @Autowired
    DailyCastService dailyCastService;
    @Value("${push.pushTarget.maxPusherCount}")
    int maxPusherCount;
    @Autowired
    ActorSystem system;

    private UserDailyTimerTargetSource userDailyTimerTargetSource;
    private DailyCastTargetSource dailyCastTargetSource;
    private PartitionalTargetSource partitionalTargetSource;

    @PostConstruct
    void init() {
        userDailyTimerTargetSource = new UserDailyTimerTargetSource(userDailyTimerService,maxPusherCount);
        partitionalTargetSource = new PartitionalTargetSource(pushTargetService,maxPusherCount);
        dailyCastTargetSource = new DailyCastTargetSource(dailyCastService,pushTargetDao,maxPusherCount);
    }

    public ITargetSource createTargetSource(CastJob job) {
        switch (job.getCastType()) {
            case UNIT:
                List<String> userList = new LinkedList<>();
                logger.debug("job.castTarget={}",job.getCastTarget());
                String[] strs = StringUtils.split(job.getCastTarget(),",");
                if (strs.length == 0) {
                    throw new IllegalArgumentException("Unitcast Job's user list can not be empty");
                }
                userList.addAll(Arrays.asList(strs));
                return new SpecifiedTargetSource(pushTargetService, userList);
            case SITUS:
                String situs = job.getCastTarget();
                return new SitusCastTargetSource(pushTargetService, situs, maxPusherCount);
            case SITUSGROUP:
                List<String> situsGroups = new LinkedList<>();
                String[] strs2 = StringUtils.split(job.getCastTarget(),",");
                if (strs2.length == 0) {
                    throw new IllegalArgumentException("SitusGroupCat Job's group list can not be empty");
                }
                situsGroups.addAll(Arrays.asList(strs2));
                return new SitusGroupCastTargetSource(pushTargetService, situsGroups, maxPusherCount);
            case BROAD:
                return partitionalTargetSource;
            case USER_DAILY_TIMER:
                return userDailyTimerTargetSource;
            case USER_DAILY:
                return dailyCastTargetSource;
            default:
                return null;
        }
    }
}
