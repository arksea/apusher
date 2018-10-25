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
    private static final int MAX_WAIT_FOR_REPLY_SECONDS = 60;
    private static final int JOB_START_DELAY_SECONDS = 45;
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
    private int jobStopDelaySeconds;
    private int waitForReplySeconds;
    private int pusherCount;
    private int noReplyEventCount; //首次进入任务结束流程时，未收到回复的推送事件数

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
        this.pusherCount = targetSource.getPusherCount(job);
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
            .match(NextPageUseTime.class,this::handleNextPageUseTime)
            .match(SubmitPushEventFailed.class, this::handleSubmitPushEventFailed)
            .match(CastJobStartDelay.class, this::handleCastJobStartDelay)
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
        jobStartDelaySeconds = random.nextInt(JOB_START_DELAY_SECONDS);
        logger.info("Start CastJob: {} after {} seconds", this.job.getId(), jobStartDelaySeconds);
        job.setRunning(true);
        beans.castJobService.saveCastJobByServer(job);
        scheduleOnce(jobStartDelaySeconds,TimeUnit.SECONDS,new CastJobStartDelay());
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
        long jobUseTime = (System.currentTimeMillis() - state.jobStartTime)/1000 - jobStopDelaySeconds - jobStartDelaySeconds;
        Integer allCount = job.getAllCount();
        logger.info("CastJob stopped: {}, pusherCount={}, allCount={}, failedCount={}, retryCount={}, noReplyEventCount={}, sumitedList={}, " +
                "jobUseTime={}s, jobStopDelay={}s, nextPageDelay={}s, submitPushEventTime={}s,getTargetsTime={}s,clientAvailableDelay={}s,userFilterTime={}s",
            this.job.getId(),
            this.pusherCount,
            allCount==null?0:allCount,
            this.job.getFailedCount(),
            this.job.getRetryCount(),
            noReplyEventCount,           //首次进入任务结束流程时，未收到回复的推送事件数
            state.submitedEvents.size(), //延迟结束任务后还剩余的没有收到回复的推送事件数
            jobUseTime,
            jobStopDelaySeconds,
            state.nextPageDelay/1000,
            state.submitPushEventTime/1000,state.getTargetsTime/1000,
            state.clientAvailableDelay /1000,state.userFilterTime/1000);
        state.payloadCache.clear();
        beans.castJobService.saveCastJobByServer(job);
    }

    private void scheduleOnce(int delay, TimeUnit timeUnit, Object msg) {
        context().system().scheduler().scheduleOnce(Duration.create(delay,timeUnit),
            self(),msg,context().dispatcher(),self());
    }

    private void handleCastJobStartDelay(CastJobStartDelay msg) throws Exception {
        String pusherName = "castjob-"+job.getId()+"-pusher";
        this.pusher = pusherFactory.create(job.getProduct(), pusherName, pusherCount, context(), tokenStatusListener);
        //延时3秒，防止PushActor刚刚建立连接，不在Avaliable状态
        scheduleOnce(3, TimeUnit.SECONDS, new PushOne());
    }

    private void handlePushOne(PushOne msg) {
        logger.trace("call handlePushOne()");
        _pushOne();
    }
    private void _pushOne() {
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
    private void _pushOneRetryEvent(PushEvent event) {
        _doPush(event, new RetrySucceed(event));
    }
    private void _pushOneTarget(PushTarget t) {
        String payload = StringUtils.isEmpty(t.getPayload()) ? job.getPayload() : t.getPayload();
        boolean isTestEvent = testTargets != null && !testTargets.contains(t.getUserId());
        //每此尝试向一个Target推送，都会新建PushEvent
        //所以要保障不向submitedEvents重复add相同target的event：
        //所以当一个Target尝试submit失败时，需要将add到submitedEvents中的event移除（handleSubmitPushEventFailed就是干这个的）
        PushEvent event = new PushEvent(job.getId()+":"+t.getUserId(),
            t.getProduct(),
            t.getToken(),
            payload,
            job.getPayloadType(),
            job.getExpiredTime().getTime(),
            isTestEvent);
        _filterUser(t, event);
    }
    private void _filterUser(PushTarget t, PushEvent event) {
        long start = System.currentTimeMillis();
        if (userFilter.doFilter(t) && !StringUtils.isEmpty(event.payload)) {
            _doPush(event,new TargetSucceed(t));
        } else { //被过滤不符合发送条件的用户不做总量计数，直接pass并设置job进度
            targetSucceed(t);
        }
        long time = System.currentTimeMillis() - start;
        state.userFilterTime += time;
    }
    private void _doPush(PushEvent event, Object succeedMsg) {
        final long start = System.currentTimeMillis();
        state.submitedEvents.add(event);
        pusher.push(event).onComplete(FutureUtils.completer(
            (ex, result) -> {
                long time = System.currentTimeMillis() - start;
                if(ex == null) {
                    if (result) {
                        self().tell(succeedMsg, self());
                    } else { //result为false表示提交推送事件失败(PushActor处于不可用状态)
                        self().tell(new SubmitPushEventFailed(event, time), ActorRef.noSender());
                    }
                } else { //超时异常表示没有可用PushActor，其他异常表示提交失败
                    self().tell(new SubmitPushEventFailed(event, time), ActorRef.noSender());
                }
            }
        ), context().dispatcher());
    }

    //------------------------------------------------------------------------------------------------------------------
    /**
     * targets列表中的的推送已正确提交的Pusher，可以开始下一轮推送
     */
    private static class TargetSucceed {
        final PushTarget target;
        final long startTime;

        private TargetSucceed(PushTarget target) {
            this.target = target;
            this.startTime = System.currentTimeMillis();
        }
    }

    private void handleTargetSucceed(TargetSucceed msg) {
        submitFailedBeginTime = 0; //提交成功必须重置“提交失败状态起始时间”为0
        state.submitPushEventTime += System.currentTimeMillis() - msg.startTime;
        //提交成功才做总量计数
        int all = 1;
        if (job.getAllCount() != null) {
            all = job.getAllCount() + 1;
        }
        this.job.setAllCount(all);
        targetSucceed(msg.target);
    }
    //完成一个target的处理（推送或不送）、设置job进度，并开始下个target的处理
    private void targetSucceed(PushTarget t) {
        PushTarget t1 = state.targets.remove(0);
        if (!t.getId().equals(t1.getId())) {
            logger.fatal("assert failed: the removed target is not specified target");
        }
        job.setLastUserId(t1.getUserId());
        _pushOne();
    }
    //------------------------------------------------------------------------------------------------------------------
    /**
     * retry列表中的推送事件已正确提交到Pusher，可以开始下一轮推送
     */
    private static class RetrySucceed {
        final PushEvent event;
        final long startTime;

        RetrySucceed(PushEvent event) {
            this.event = event;
            this.startTime = System.currentTimeMillis();
        }
    }

    private void handleRetrySucceed(RetrySucceed msg) {
        state.retryEvents.remove(msg.event);
        msg.event.incRetryCount();
        job.setRetryCount(job.getRetryCount()+1);
        _pushOne();
    }

    //------------------------------------------------------------------------------------------------------------------
    /**
     * 提交PushEvent到pusher是因没有pusher处于available而失败
     */
    static class SubmitPushEventFailed {
        final PushEvent event;
        final long time;
        SubmitPushEventFailed(PushEvent event, long time) {
            this.event = event;
            this.time = time;
        }
    }
    private void handleSubmitPushEventFailed(SubmitPushEventFailed msg) {
        boolean removed = state.submitedEvents.remove(msg.event);
        if (!removed) {
            logger.warn("assert failed: event not in submited list!");
        }
        long now = System.currentTimeMillis();
        if (submitFailedBeginTime <= 0) {
            submitFailedBeginTime = now;
        }
        state.clientAvailableDelay += msg.time;
        long minutes = (now - submitFailedBeginTime) / 60000;
        if (minutes > 10) { //持续10分钟以上不能提交推送事件，则退出本次推送任务
            delayFinishJob("It is not possible to submit push events for more than " + minutes + " minutes");
        } else {
            _pushOne();
        }
    }
    //------------------------------------------------------------------------------------------------------------------
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
            long start = System.currentTimeMillis();
            beans.castJobService.saveCastJobByServer(job);
            // 计入nextPageDelay，这样可以通过计算得到保存job状态花费的时间：
            // nextPageDealy - 1024*NEXT_PAGE_DELAY_MILLI
            state.nextPageDelay += System.currentTimeMillis() - start;
            delayNextPage(false);
        } else {
            _pushOne();
        }
    }

    private void delayNextPage(boolean isFailedRetry) {
        NextPage nextPage = new NextPage(isFailedRetry);
        if (NEXT_PAGE_DELAY_MILLI > 0) {
            scheduleOnce(NEXT_PAGE_DELAY_MILLI, TimeUnit.MILLISECONDS, nextPage);
        } else {
            self().tell(nextPage, self());
        }
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

    //延迟结束任务，等待submitEvent中已提交推送回执消息
    private void delayFinishJob(String status) {
        scheduleOnce(JOB_FINISHE_DELAY_SECONDS,TimeUnit.SECONDS,new JobFinished(status));
    }

    private void finishJob() {
        job.setFinishedTime(new Timestamp(System.currentTimeMillis()));
        job.setRunning(false);
        context().stop(self());
    }

    private void handleJobFinished(JobFinished msg) {
        if (jobStopDelaySeconds == 0) {
            noReplyEventCount = state.submitedEvents.size();
        }
        this.jobStopDelaySeconds += JOB_FINISHE_DELAY_SECONDS;
        this.waitForReplySeconds += JOB_FINISHE_DELAY_SECONDS;
        if (state.submitedEvents.size() == 0) {
            finishJob();
        } else if (waitForReplySeconds < MAX_WAIT_FOR_REPLY_SECONDS) {
            delayFinishJob(msg.status);
        } else {
            finishJob();
//            //没有收到回执消息的推送将被重发，有可能造成少量重复的推送消息，任务继续执行
//            for (PushEvent e : state.submitedEvents) {
//                if (e.getRetryCount() < MAX_RETRY_PUSH) {
//                    state.retryEvents.add(e);
//                } else {
//                    int all = 1;
//                    if (job.getAllCount() != null) {
//                        all = job.getAllCount() + 1;
//                    }
//                    this.job.setAllCount(all);
//                    int failed = job.getFailedCount() + 1;
//                    this.job.setFailedCount(failed);
//                }
//            }
//            state.submitedEvents.clear();
//            this.waitForReplySeconds = 0;
//            pushNext();
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    static class CastJobStartDelay {
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

    private static class JobFinished{
        final String status;
        JobFinished(String status) {
            this.status = status;
        }
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
