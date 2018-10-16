package net.arksea.pusher.server.service;

import akka.actor.AbstractActor;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.japi.Creator;
import net.arksea.pusher.server.Partition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.Option;
import scala.concurrent.duration.Duration;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 定时清理PushTarget表与UserDailyTimer表
 * Created by xiaohaixing on 2017/11/8.
 */
public class PushTargetManager extends AbstractActor {

    private final static Logger logger = LogManager.getLogger(PushTargetManager.class);
    private PushTargetManagerState state;
    private Cancellable timerClean;
    private Cancellable timerDel;
    private int partition;
    private Timestamp lastCleanBefore;

    public PushTargetManager(PushTargetManagerState state) {
        this.state = state;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(CleanTimer.class,  this::onCleanTimer)
            .match(DeleteTimer.class,  this::onDeleteTimer)
            .build();
    }

    public static Props props(PushTargetManagerState state) {
        return Props.create(PushTargetManager.class, new Creator<PushTargetManager>() {
            @Override
            public PushTargetManager create() throws Exception {
                return new PushTargetManager(state);
            }
        });
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        timerClean = context().system().scheduler().schedule(
            Duration.create(35, TimeUnit.SECONDS),
            Duration.create(state.pushTargetAutoCleanPeriodMinutes,TimeUnit.MINUTES),
            self(),new CleanTimer(),context().dispatcher(),self());

        //不按分区删除则开启此定时器，在夜间3~4点间执行批量删除
        if (!state.pushTargetDeleteByPartition) {
            timerDel = context().system().scheduler().schedule(
                Duration.create(1, TimeUnit.MINUTES),
                Duration.create(1, TimeUnit.HOURS),
                self(), new DeleteTimer(), context().dispatcher(), self());
        }
        logger.info("Start PushTargetManager");
    }

    @Override
    public void preRestart(Throwable ex, Option<Object> msg) throws Exception {
        super.preRestart(ex, msg);
        logger.warn("PushTargetManager restarting because exception", ex);
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        logger.info("PushTargetManager stopped");
        if (timerClean != null) {
            timerClean.cancel();
            timerClean = null;
        }
        if (timerDel != null) {
            timerDel.cancel();
            timerDel = null;
        }
    }

    private void onCleanTimer(CleanTimer msg) {
        lastCleanBefore = Timestamp.valueOf(LocalDateTime.now().plusDays(-1*state.pushTargetAutoCleanDays));
        logger.debug("to clean befor {} PushTargets, partition={}", lastCleanBefore,partition);
        state.pushTargetService.cleanBefore(partition, lastCleanBefore);
        if (state.pushTargetDeleteByPartition) {
            state.pushTargetService.deleteBeforeByPartiton(partition, lastCleanBefore);
        }
        partition++;
        if (partition >= Partition.MAX_USER_PARTITION) {
            partition = 0;
        }
    }

    private void onDeleteTimer(DeleteTimer msg) {
        logger.debug("onDeleteTimer");
        LocalDateTime dt = LocalDateTime.now();
        if (dt.getHour() == 3) {
            state.pushTargetService.deleteBefore(lastCleanBefore);
        }
    }

    public static class CleanTimer {}  //删除标记定时器，每5分钟标记一次，将无效target用户的UserDailyTimer记录标记为deleted
    public static class DeleteTimer {} //删除定时器，每天3~4点间做一次批量删除
}
