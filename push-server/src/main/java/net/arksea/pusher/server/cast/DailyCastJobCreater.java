package net.arksea.pusher.server.cast;

import akka.actor.AbstractActor;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.japi.Creator;
import net.arksea.pusher.entity.DailyCast;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.Option;
import scala.concurrent.duration.Duration;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 定时遍历DailyCast表，生成定时任务并插入CastJob表
 * Created by xiaohaixing on 2017/11/8.
 */
public class DailyCastJobCreater extends AbstractActor {

    private final static Logger logger = LogManager.getLogger(DailyCastJobCreater.class);
    private JobResources beans;
    private Cancellable timer;

    public DailyCastJobCreater(final JobResources beans) {
        this.beans = beans;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(CastJobPollingTimer.class,  this::onTimer)
            .build();
    }

    public static Props props(final JobResources beans) {
        return Props.create(DailyCastJobCreater.class, new Creator<DailyCastJobCreater>() {
            @Override
            public DailyCastJobCreater create() throws Exception {
                return new DailyCastJobCreater(beans);
            }
        });
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        timer = context().system().scheduler().schedule(
            Duration.create(1, TimeUnit.MINUTES),
            Duration.create(10, TimeUnit.MINUTES),
            self(),new CastJobPollingTimer(),context().dispatcher(),self());
        logger.info("Start DailyCastJobCreater");
    }

    @Override
    public void preRestart(Throwable ex, Option<Object> msg) throws Exception {
        super.preRestart(ex, msg);
        logger.warn("DailyCastJobCreater restarting because exception", ex);
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        logger.info("DailyCastJobCreater stopped");
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    final static ZoneId zoneId = ZoneId.of("+8");

    //创建需要执行的job
    private void onTimer(CastJobPollingTimer msg) {
        logger.trace("on DailyCastJobCreater.CastJobPollingTimer");
        ZonedDateTime startOfToday = LocalDate.now().atStartOfDay(zoneId);
        int page = 0;
        int pageSize = 100;
        String dayOfWeek = Integer.toString(LocalDate.now().getDayOfWeek().getValue());
        List<DailyCast> castList = beans.dailyCastService.getNotCreated(startOfToday, page, pageSize);
        while (castList != null && castList.size() > 0) {
            logger.debug("creating jobs: startTime = {}, mached DailyCast count = {}", startOfToday, castList.size());
            for (DailyCast cast : castList) {
                if (StringUtils.isEmpty(cast.getDays()) || cast.getDays().indexOf(dayOfWeek)>=0) {
                    beans.dailyCastService.addCastJob(startOfToday, cast);
                }
            }
            ++page;
            castList = beans.dailyCastService.getNotCreated(startOfToday, page, pageSize);
        }
    }

    public static class CastJobPollingTimer {}
}
