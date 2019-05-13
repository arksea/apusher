package net.arksea.pusher.server.cast;

import akka.actor.AbstractActor;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.japi.Creator;
import net.arksea.pusher.entity.UserDailyCast;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.Option;
import scala.concurrent.duration.Duration;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 定时遍历UserDailyCast表，生成定时任务并插入CastJob表
 * Created by xiaohaixing on 2017/11/8.
 */
public class UserDailyCastJobCreater extends AbstractActor {

    private final static Logger logger = LogManager.getLogger(UserDailyCastJobCreater.class);
    private JobResources beans;
    private Cancellable timer;

    public UserDailyCastJobCreater(final JobResources beans) {
        this.beans = beans;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(CastJobPollingTimer.class,  this::onTimer)
            .build();
    }

    public static Props props(final JobResources beans) {
        return Props.create(UserDailyCastJobCreater.class, new Creator<UserDailyCastJobCreater>() {
            @Override
            public UserDailyCastJobCreater create() throws Exception {
                return new UserDailyCastJobCreater(beans);
            }
        });
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        timer = context().system().scheduler().schedule(
            Duration.create(30, TimeUnit.SECONDS),
            Duration.create(1, TimeUnit.MINUTES),
            self(),new CastJobPollingTimer(),context().dispatcher(),self());
        logger.info("Start UserDailyCastJobCreater");
    }

    @Override
    public void preRestart(Throwable ex, Option<Object> msg) throws Exception {
        super.preRestart(ex, msg);
        logger.warn("UserDailyCastJobCreater restarting because exception", ex);
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        logger.info("UserDailyCastJobCreater stopped");
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    final static ZoneId zoneId = ZoneId.of("+8");

    //创建需要执行的job
    private void onTimer(CastJobPollingTimer msg) {
        logger.trace("on UserDailyCastJobCreater.CastJobPollingTimer");
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        int minuteOfDay = (now.getHour()*60 + now.getMinute()) / 10 * 10; //取整10分钟的时间点
        ZonedDateTime startOfToday = LocalDate.now().atStartOfDay(zoneId);
        ZonedDateTime jobStartTime = startOfToday.plusMinutes(minuteOfDay);
        int page = 0;
        int pageSize = 100;
        List<UserDailyCast> castList = beans.userDailyCastService.getNotCreated(jobStartTime, page, pageSize);
        while (castList != null && castList.size() > 0) {
            logger.debug("creating jobs: startTime = {}, mached UserDailyCast count = {}", jobStartTime, castList.size());
            for (UserDailyCast cast : castList) {
                beans.userDailyCastService.addCastJob(jobStartTime, cast);
            }
            ++page;
            castList = beans.userDailyCastService.getNotCreated(jobStartTime, page, pageSize);
        }
    }

    public static class CastJobPollingTimer {}
}
