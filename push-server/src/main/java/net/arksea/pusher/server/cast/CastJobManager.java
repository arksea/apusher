package net.arksea.pusher.server.cast;

import akka.actor.*;
import akka.japi.Creator;
import net.arksea.pusher.IPushClientFactory;
import net.arksea.pusher.entity.CastJob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.Option;
import scala.concurrent.duration.Duration;

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
    private IPushClientFactory pushClientFactory;
    class ProductInfo {
        public final String product;
        public int jobCount;

        public ProductInfo(String product) {
            this.product = product;
        }
    }

    public CastJobManager(CastJobManagerState state) {
        this.state = state;
        try {
            Class clazz = Class.forName(state.pushClientFactoryClass);
            pushClientFactory = (IPushClientFactory)clazz.newInstance();
        } catch (Exception ex) {
            throw new RuntimeException("Create PusherFactory failed:" + state.pushClientFactoryClass, ex);
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Terminated.class, this::onTerminated)
            .match(CastJobPollingTimer.class,  this::onTimer).build();
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
        timer.cancel();
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
                ActorRef ref = context().actorOf(CastJobActor.props(state.jobResources, job, targetSource, pushClientFactory),jobName);
                context().watch(ref);
                ++productInfo.jobCount;
                state.jobMap.put(ref, productInfo);
            } catch (Exception ex) {
                logger.warn("Start CastJob({}) failed", job.getId(), ex);
            }
        }
    }

    public static class CastJobPollingTimer {
    }
}
