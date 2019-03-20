package net.arksea.pusher.server.cast;

import akka.actor.*;
import akka.japi.Creator;
import net.arksea.pusher.entity.CastJob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.Option;
import scala.concurrent.duration.Duration;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2017/11/8.
 */
public class CastJobManager extends AbstractActor {

    private final static Logger logger = LogManager.getLogger(CastJobManager.class);
    private Cancellable timer;
    private CastJobManagerState state;
    private Cancellable jobCleanTimer;
    class ProductInfo {
        public final String product;
        public int jobCount;

        public ProductInfo(String product) {
            this.product = product;
        }
    }

    public CastJobManager(CastJobManagerState state) {
        this.state = state;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Terminated.class, this::onTerminated)
            .match(CastJobPollingTimer.class,  this::onTimer)
            .match(JobCleanTimer.class,  this::onJobCleanTimer)
            .build();
    }

    public static Props props(final CastJobManagerState state) {
        return Props.create(CastJobManager.class, new Creator<CastJobManager>() {
            @Override
            public CastJobManager create() throws Exception {
                return new CastJobManager(state);
            }
        });
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        state.castJobService.resetRunningCastJob(); //重置所有任务的运行状态，防止推送服务异常退出造成运行状态错误
        timer = context().system().scheduler().schedule(
            Duration.create(10, TimeUnit.SECONDS),
            Duration.create(1,TimeUnit.MINUTES),
            self(),new CastJobPollingTimer(),context().dispatcher(),self());
        if (state.cleanJobDays > 0) {
            jobCleanTimer = context().system().scheduler().schedule(
                Duration.create(1, TimeUnit.MINUTES),
                Duration.create(1000, TimeUnit.MINUTES),
                self(),new JobCleanTimer(),context().dispatcher(),self());
        }
        logger.info("Start CastJobManager");
    }

    @Override
    public void preRestart(Throwable ex, Option<Object> msg) throws Exception {
        super.preRestart(ex, msg);
        logger.warn("CastJobManager restarting because exception", ex);
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        logger.info("CastJobManager stopped");
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (jobCleanTimer != null) {
            jobCleanTimer.cancel();
            jobCleanTimer = null;
        }
    }

    private void onTimer(CastJobPollingTimer msg) {
        logger.trace("on CastJobPollingTimer");
        List<CastJob> u = state.castJobService.getOnTimeCastJobs();
        for (CastJob job : u) {
            startJob(job);
        }
    }

    private void onTerminated(Terminated msg) {
        ProductInfo productInfo = state.jobMap.remove(msg.getActor());
        --productInfo.jobCount;
        logger.debug("product {} job {} terminated", productInfo.product, msg.actor().path().toStringWithoutAddress());
    }

    private void startJob(CastJob job) {
        //限制每个product同时并行的Pusher数
        ProductInfo productInfo = state.productInfoMap.computeIfAbsent(job.getProduct(), ProductInfo::new);
        if (productInfo.jobCount < state.maxJobsPerProduct) {
            try {
                ITargetSource targetSource = state.targetSourceFactory.createTargetSource(job);
                String jobName = "castjob-"+job.getId();
                ActorRef ref = context().actorOf(CastJobActor.props(state.jobResources, job, targetSource, state.pushClientFactory),jobName);
                context().watch(ref);
                ++productInfo.jobCount;
                state.jobMap.put(ref, productInfo);
            } catch (Exception ex) {
                logger.warn("Start CastJob({}) failed", job.getId(), ex);
            }
        }
    }

    private void onJobCleanTimer(JobCleanTimer msg) {
        long now = System.currentTimeMillis();
        long sec = state.cleanJobDays * 86_400; //毫秒用long会越界
        Timestamp jobCleanTime = new Timestamp((now/1000 - sec) * 1000);
        int n = state.castJobService.deleteOldCastJob(jobCleanTime);
        logger.info("delete CastJob that start time before {}, count={} ,use {} ms",
            jobCleanTime.toString(), n, System.currentTimeMillis() - now);
    }

    public static class CastJobPollingTimer {}
    public static class JobCleanTimer{}
}
