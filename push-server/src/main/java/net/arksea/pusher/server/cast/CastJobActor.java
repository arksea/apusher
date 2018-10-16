package net.arksea.pusher.server.cast;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.dispatch.OnComplete;
import akka.japi.Creator;
import groovy.json.JsonSlurper;
import net.arksea.base.FutureUtils;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.IPusher;
import net.arksea.pusher.IPusherFactory;
import net.arksea.pusher.PushEvent;
import net.arksea.pusher.entity.CastJob;
import net.arksea.pusher.entity.PushTarget;
import net.arksea.pusher.server.Partition;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.Option;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2017/11/8.
 */
public class CastJobActor extends AbstractActor {
    private final static Logger logger = LogManager.getLogger(CastJobActor.class);

    private static final int JOB_FINISHE_DELAY_SECONDS = 5;
    private static final int JOB_START_DELAY_SECONDS = 30;
    private static final int NEXT_PAGE_DELAY_MILLI = 10;
    private static final int MAX_RETRY_PUSH = 3;
    private static final int MAX_RETRY_NEXTPAGE = 5;

    private final State state;
    private final JobResources beans;
    private final CastJob job;
    private final UserFilter userFilter;
    private Set<String> testTargets = null;
    private final ITargetSource targetSource;
    private IPusherFactory pusherFactory;
    private IPusher pusher;

    private long submitFailedBeginTime;
    private final static JsonSlurper jsonSlurper = new JsonSlurper();
    private final static Random random = new Random(System.currentTimeMillis());
    private final IPushStatusListener tokenStatusListener;
    private int jobStartDelaySeconds;

    private CastJobActor(State state, JobResources beans, CastJob job, ITargetSource targetSource,
                         UserFilter userFilter, IPusherFactory pusherFactory) {
        this.state = state;
        this.beans = beans;
        this.job = job;
        if (!StringUtils.isEmpty(job.getTestTarget())) {
            testTargets = new HashSet<>();
            String[] strs = StringUtils.split(job.getTestTarget(), ",");
            logger.debug("The job({}) has {} test targets", job.getId(), strs.length);
            for (String s : strs) {
                testTargets.add(s);
            }
        }
        this.userFilter = userFilter;
        this.targetSource = targetSource;
        this.pusherFactory = pusherFactory;
        this.tokenStatusListener = new PushStatusListener(self(), beans);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(PushOne.class, this::handlePushOne)
            .match(TargetSucceed.class,this::handleTargetSucceed)
            .match(RetrySucceed.class, this::handleRetrySucceed)
            .match(PushSucceed.class,  this::handlePushSucceed)
            .match(PushFailed.class,   this::handlePushFailed)
            .match(PushInvalid.class,   this::handlePushInvalid)
            .match(NextPage.class,     this::handleNextPage)
            .match(PageTargets.class,  this::handlePageTargets)
            .match(JobFinished.class,  this::handleJobFinished)
            .match(SubmitPushEventTime.class,this::handleSubmitPushEventTime)
            .match(NextPageUseTime.class,this::handleNextPageUseTime)
            .match(ClientAvailableDelay.class, this::handleClientAvailableDelay)
            .build();
    }

    static class State {
        List<PushTarget> targets;
        Set<PushEvent> retryEvents = new HashSet<>();
        Set<PushEvent> submitedEvents = new HashSet<>();
        Map<String,String> payloadCache = new ConcurrentHashMap<>();
        int retryNextPageCount;
        long jobStartTime;         //任务提交时间
        long submitPushEventTime;  //提交pushEvent用时
        long getTargetsTime;       //请求target用时
        long clientAvailableDelay; //Pusher未可以用延时时间
        long nextPageDelay;        //nextPage总延时
        long userFilterTime;       //userFilter用时
        State() {
            jobStartTime = System.currentTimeMillis();
        }
    }

    static Props props(JobResources beans, CastJob job, ITargetSource targetSource, IPusherFactory pusherFactory) throws Exception {
        String script = "";
        if (!StringUtils.isEmpty(job.getUserFilter())) {
            Map map =  (Map)jsonSlurper.parseText(job.getUserFilter());
            script = (String)map.get("script");
        }
        UserFilter userFilter = new UserFilter(script);
        State state = new State();
        return Props.create(CastJobActor.class, new Creator<CastJobActor>() {
            @Override
            public CastJobActor create() throws Exception {
                return new CastJobActor(state, beans, job, targetSource, userFilter, pusherFactory);
            }
        });
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        //此延时如果太短，可能Pusher中的PushActor都不在Avaliable状态，所以设置了一个最小值5秒
        jobStartDelaySeconds = 5 + random.nextInt(JOB_START_DELAY_SECONDS);
        logger.info("Start CastJob: {} after {} seconds", this.job.getId(), jobStartDelaySeconds);
        int pusherCount = targetSource.getPusherCount(job);
        String pusherName = "castjob-"+job.getId()+"-pusher";
        this.pusher = pusherFactory.create(job.getProduct(), pusherName, pusherCount, context(), tokenStatusListener);
        job.setRunning(true);
        beans.castJobService.saveCastJobByServer(job);
        delayPushNext(jobStartDelaySeconds*1000);
    }

    @Override
    public void preRestart(Throwable reason, Option<Object> message) throws Exception  {
        super.preRestart(reason, message);
        if (message.nonEmpty()) {
            Object obj = message.get();
            if (obj instanceof PushFailed) {
                PushFailed msg = (PushFailed) obj;
                handlePushFailed(msg);
            } else if (obj instanceof PushSucceed) {
                PushSucceed msg = (PushSucceed) obj;
                handlePushSucceed(msg);
            }
        }
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        long jobUseTime = (System.currentTimeMillis() - state.jobStartTime)/1000 - JOB_FINISHE_DELAY_SECONDS - jobStartDelaySeconds;
        Integer allCount = job.getAllCount();
        logger.info("CastJob stopped: {}, allCount={}, failedCount={}, retryCount={}, sumitedList={}, jobUseTime={}s, nextPageDelay={}s, submitPushEventTime={}s,getTargetsTime={}s,clientAvailableDelay={}s,userFilterTime={}s",
            this.job.getId(),
            allCount==null?0:allCount,
            this.job.getFailedCount(),
            this.job.getRetryCount(),
            state.submitedEvents.size(),
            jobUseTime,
            state.nextPageDelay/1000,
            state.submitPushEventTime/1000,state.getTargetsTime/1000,
            state.clientAvailableDelay /1000,state.userFilterTime/1000);
        state.payloadCache.clear();
        beans.castJobService.saveCastJobByServer(job);
    }

    private void handlePushOne(PushOne msg) throws Exception {
        logger.trace("call pushOne()");
        Iterator<PushEvent> it = state.retryEvents.iterator();
        if (it.hasNext()) {
            _pushOneRetryEvent(it.next());
        } else if (state.targets == null || state.targets.isEmpty()) {
            delayNextPage(false);
        } else {
            PushTarget t = state.targets.get(0);
            _pushOneTarget(t);
        }
    }
    private void _pushOneRetryEvent(PushEvent event) throws Exception {
        _doPush(event, new RetrySucceed(event));
    }
    private void _pushOneTarget(PushTarget t) throws Exception {
        String payload = StringUtils.isEmpty(t.getPayload()) ? job.getPayload() : t.getPayload();
        boolean isTestEvent = testTargets != null && !testTargets.contains(t.getUserId());
        PushEvent event = new PushEvent(job.getId()+":"+t.getUserId(),
            t.getProduct(),
            t.getToken(),
            payload,
            job.getPayloadType(),
            job.getExpiredTime().getTime(),
            isTestEvent);
        _filterUser(t, event);
    }
    private void _filterUser(PushTarget t, PushEvent event) throws Exception {
        long start = System.currentTimeMillis();
        if (userFilter.doFilter(t) && !StringUtils.isEmpty(event.payload)) {
            _doPush(event,new TargetSucceed(t, event));
        } else { //被过滤不符合发送条件的用户只做总量计数， 所以 过滤量=总数-成功数-失败数
            int all = 1;
            if (job.getAllCount() != null) {
                all = job.getAllCount() + 1;
            }
            this.job.setAllCount(all);
            targetSucceed(t);
        }
        long time = System.currentTimeMillis() - start;
        state.userFilterTime += time;
    }
    private void _doPush(PushEvent event, Object succeedMsg) throws Exception {
        final long start = System.currentTimeMillis();
        pusher.push(event).onComplete(FutureUtils.completer(
            (ex, result) -> {
                long time = System.currentTimeMillis() - start;
                if(ex == null) {
                    submitFailedBeginTime = 0; //提交成功必须重置“提交失败状态起始时间”为0
                    if (result) {
                        self().tell(succeedMsg, self());
                    } else { //result为false表示提交推送事件失败(PushActor处于不可用状态)
                        pushNext();
                    }
                    self().tell(new SubmitPushEventTime(time), ActorRef.noSender());
                } else { //超时异常表示没有可用PushActor，其他异常表示提交失败
                    if (submitFailedBeginTime <= 0) {
                        submitFailedBeginTime = start;
                    }
                    long minutes = (start - submitFailedBeginTime) / 60000;
                    if (minutes > 10) { //持续10分钟以上不能提交推送事件，则退出本次推送任务
                        delayFinishJob("It is not possible to submit push events for more than " + minutes + " minutes");
                    } else {
                        pushNext();
                        self().tell(new ClientAvailableDelay(time), ActorRef.noSender());
                    }
                }
            }
        ), context().dispatcher());
        state.submitedEvents.add(event);
    }

    private void handleTargetSucceed(TargetSucceed msg) {
        targetSucceed(msg.target);
    }
    private void targetSucceed(PushTarget t) {
        PushTarget t1 = state.targets.remove(0);
        if (!t.getId().equals(t1.getId())) {
            logger.warn("assert failed: the removed target is not specified target");
        }
        job.setLastUserId(t1.getUserId());
        pushNext();
    }

    private void handleRetrySucceed(RetrySucceed msg) {
        state.retryEvents.remove(msg.event);
        msg.event.incRetryCount();
        job.setRetryCount(job.getRetryCount()+1);
        pushNext();
    }

    private void handleNextPage(NextPage msg) {
        logger.trace("call nextPage(msg), partition={}", job.getLastPartition());
        state.nextPageDelay += NEXT_PAGE_DELAY_MILLI;
        if (msg.isFailedRetry) {
            ++state.retryNextPageCount;
        } else {
            state.retryNextPageCount = 0;
        }
        if (state.retryNextPageCount < MAX_RETRY_NEXTPAGE) {
            int partition = job.getLastPartition();
            if (partition < Partition.MAX_USER_PARTITION) {
                final long start = System.currentTimeMillis();
                Future<List<PushTarget>> future = targetSource.nextPage(job, state.payloadCache);
                future.onComplete(new OnComplete<List<PushTarget>>() {
                    @Override
                    public void onComplete(Throwable failure, List<PushTarget> targets) throws Throwable {
                        long time = System.currentTimeMillis() - start;
                        self().tell(new NextPageUseTime(time), ActorRef.noSender());
                        if (failure == null && targets != null) {
                            getPageTargetsSucceed(targets);
                        } else {
                            logger.error("get next page targets failed", failure);
                            delayNextPage(true);
                        }
                    }
                }, context().dispatcher());
            } else {
                delayFinishJob("succeed");
            }
        } else {
            delayFinishJob("get next page targets failed");
        }
    }

    private void handlePageTargets(PageTargets msg) {
        state.targets = msg.targets;
        state.retryNextPageCount = 0;
        if (state.targets == null || state.targets.isEmpty()) {
            int partition = job.getLastPartition() + 1;
            job.setLastPartition(partition);
            //修改partition必须重新设置userId，否则可能会将一个分区的uid设置到另一个分区上，这可能会造成遗漏部分用户
            job.setLastUserId(null);
            beans.castJobService.saveCastJobByServer(job);
            delayNextPage(false);
        } else {
            pushNext();
        }
    }

    private void delayNextPage(boolean isFailedRetry) {
        NextPage nextPage = new NextPage(isFailedRetry);
        if (NEXT_PAGE_DELAY_MILLI > 0) {
            context().system().scheduler().scheduleOnce(
                Duration.create(NEXT_PAGE_DELAY_MILLI, TimeUnit.MILLISECONDS),
                self(), nextPage, context().dispatcher(), self());
        } else {
            self().tell(nextPage, self());
        }
    }

    private void pushNext() {
        self().tell(new PushOne(), self());
    }
    private void getPageTargetsSucceed(List<PushTarget> targets) {
        List<PushTarget> valid = new LinkedList<>();
        for (PushTarget t: targets) {
            if (StringUtils.isEmpty(t.getToken()) ||
                StringUtils.isEmpty(job.getPayload()) && StringUtils.isEmpty(t.getPayload())) {
                continue;
            } else {
                valid.add(t);
            }
        }
        self().tell(new PageTargets(valid), self());
    }

    private void delayPushNext(int delayMilli) {
        context().system().scheduler().scheduleOnce(
            Duration.create(delayMilli,TimeUnit.MILLISECONDS),
            self(),new PushOne(),context().dispatcher(),self());
    }

    private void delayFinishJob(String status) {
        job.setFinishedTime(new Timestamp(System.currentTimeMillis()));
        job.setRunning(false);
        context().system().scheduler().scheduleOnce(
            Duration.create(JOB_FINISHE_DELAY_SECONDS,TimeUnit.SECONDS),
            self(),new JobFinished(status),context().dispatcher(),self());
    }

    private void handleJobFinished(JobFinished msg) {
        context().stop(self());
    }

    //----------------------------------------------------------------------------------------------------------
    /**
     * pusher已获得推送平台推送成功的回执应答，可以进行推送成功次数的计数
     */
    static class PushSucceed {
        public final PushEvent event;
        public PushSucceed(PushEvent event) {
            this.event = event;
        }
    }
    private void handlePushSucceed(PushSucceed msg) {
        logger.trace("call onPushSucceed(msg), topic={}, token={}",msg.event.topic, msg.event.token);
        boolean removed = state.submitedEvents.remove(msg.event);
        if (!removed) {
            logger.warn("assert failed: event not in submited list!");
        }
        int all = 1;
        if (job.getAllCount() != null) {
            all = job.getAllCount() + 1;
        }
        this.job.setAllCount(all);
        int succeed = 1;
        if (job.getSucceedCount() != null) {
            succeed = job.getSucceedCount() + 1;
        }
        this.job.setSucceedCount(succeed);
    }

    //----------------------------------------------------------------------------------------------------------
    /**
     * pusher已获得推送平台推送失败的回执应答，可以进行推送失败次数的计数，
     * 并将失败的event放到失败列表中，等待在下一轮推送中重试
     */
    static class PushFailed {
        public final PushEvent event;
        public PushFailed(PushEvent event) {
            this.event = event;
        }
    }
    private void handlePushFailed(PushFailed msg) {
        logger.trace("call onPushFailed(msg), topic={}, token={}",msg.event.topic, msg.event.token);
        boolean removed = state.submitedEvents.remove(msg.event);
        if (!removed) {
            logger.warn("assert failed: event not in submited list!");
        }
        if (msg.event.getRetryCount() < MAX_RETRY_PUSH) {
            state.retryEvents.add(msg.event);
        } else {
            int all = 1;
            if (job.getAllCount() != null) {
                all = job.getAllCount() + 1;
            }
            this.job.setAllCount(all);
            int failed = job.getFailedCount() + 1;
            this.job.setFailedCount(failed);
        }
    }
    //----------------------------------------------------------------------------------------------------------
    /**
     * 用户状态异常造成的推送失败，不做推送成功与失败计数
     */
    static class PushInvalid {
        public final PushEvent event;
        public PushInvalid(PushEvent event) {
            this.event = event;
        }
    }
    public void handlePushInvalid(PushInvalid msg) {
        logger.trace("call onPushValid(msg), topic={}, token={}",msg.event.topic, msg.event.token);
        boolean removed = state.submitedEvents.remove(msg.event);
        if (!removed) {
            logger.warn("event not in submited list!");
        }
    }
    //----------------------------------------------------------------------------------------------------------
    private static class PushOne {}
    private static class PageTargets {
        final List<PushTarget> targets;
        PageTargets(List<PushTarget> targets) {
            this.targets = targets;
        }
    }
    private static class NextPage {
        final boolean isFailedRetry;

        NextPage(boolean isFailedRetry) {
            this.isFailedRetry = isFailedRetry;
        }
    }

    /**
     * retry列表中的推送事件已正确提交到Pusher，可以开始下一轮推送
     */
    private static class RetrySucceed {
        final PushEvent event;

        RetrySucceed(PushEvent event) {
            this.event = event;
        }
    }

    /**
     * targets列表中的的推送已正确提交的Pusher，可以开始下一轮推送
     */
    private static class TargetSucceed {
        final PushTarget target;
        final PushEvent event;

        private TargetSucceed(PushTarget target, PushEvent event) {
            this.target = target;
            this.event = event;
        }
    }

    private static class JobFinished{
        final String status;
        JobFinished(String status) {
            this.status = status;
        }
    }
    //------------------------------------------------------------------------------------------------------------------
    /**
     * 统计提交PushEvent到pusher的用时
     */
    static class SubmitPushEventTime {
        final long time;
        SubmitPushEventTime(long time) {
            this.time = time;
        }
    }
    private void handleSubmitPushEventTime(SubmitPushEventTime msg) {
        state.submitPushEventTime += msg.time;
    }
    //------------------------------------------------------------------------------------------------------------------
    /**
     * 统计提交PushEvent到pusher的用时
     */
    static class ClientAvailableDelay {
        public final long time;
        ClientAvailableDelay(long time) {
            this.time = time;
        }
    }
    void handleClientAvailableDelay(ClientAvailableDelay msg) {
        state.clientAvailableDelay += msg.time;
    }
    //------------------------------------------------------------------------------------------------------------------
    static class NextPageUseTime {
        final long time;
        NextPageUseTime(long time) {
            this.time = time;
        }
    }
    private void handleNextPageUseTime(NextPageUseTime msg) {
        state.getTargetsTime += msg.time;
    }
}
