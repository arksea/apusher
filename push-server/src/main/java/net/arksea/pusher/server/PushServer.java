package net.arksea.pusher.server;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.Creator;
import net.arksea.dsf.service.ServiceRequest;
import net.arksea.dsf.service.ServiceResponse;
import net.arksea.pusher.PushResult;
import net.arksea.pusher.entity.*;
import net.arksea.pusher.request.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.Option;

import java.util.List;

/**
 *
 * Created by xiaohaixing on 2017/7/6.
 */

public class PushServer extends AbstractActor {
    private final static Logger logger = LogManager.getLogger(PushServer.class);
    PushServerStat stat;

    public PushServer(final PushServerStat stat) {
        this.stat = stat;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(ServiceRequest.class, this::onServiceRequest)
            .build();
    }

    public static Props props(final PushServerStat stat) {
        return Props.create(PushServer.class, new Creator<PushServer>() {
            @Override
            public PushServer create() throws Exception {
                return new PushServer(stat);
            }
        });
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        logger.info("Start PushServer");
    }

    @Override
    public void preRestart(Throwable ex, Option<Object> msg) throws Exception {
        super.preRestart(ex, msg);
        logger.warn("PushServer restarting because exception", ex);
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        logger.info("PushServer stopped");
    }

    private void onServiceRequest(ServiceRequest request) {
        if (request.message instanceof UpdateSitus) {
            updateSitus((UpdateSitus)request.message, request);
        } else if (request.message instanceof UpdateToken) {
            updateToken((UpdateToken)request.message, request);
        } else if (request.message instanceof UpdateTokenStatus) {
            updateTokenStatus((UpdateTokenStatus)request.message, request);
        } else if (request.message instanceof GetUserDailyTimers) {
            getUserDailyTimers((GetUserDailyTimers)request.message, request);
        } else if (request.message instanceof UpdateUserDailyTimer) {
            updateUserDailyTimer((UpdateUserDailyTimer)request.message, request);
        } else if (request.message instanceof DeleteUserDailyTimer) {
            deleteUserDailyTimer((DeleteUserDailyTimer)request.message, request);
        } else if (request.message instanceof UpdateUserDailyTimerStatus) {
            updateUserDailyTimerStatus((UpdateUserDailyTimerStatus)request.message, request);
        } else if (request.message instanceof DeleteCastJob) {
            deleteCastJob((DeleteCastJob)request.message, request);
        } else if (request.message instanceof AddCastJobs) {
            addCastJobs((AddCastJobs)request.message, request);
        } else if (request.message instanceof AddCastJob) {
            addCastJob((AddCastJob)request.message, request);
        } else if (request.message instanceof GetCastJobs) {
            getCastJobs((GetCastJobs)request.message, request);
        } else if (request.message instanceof GetCastJob) {
            getCastJob((GetCastJob)request.message, request);
        } else if (request.message instanceof UpdateCastJob) {
            updateCastJob((UpdateCastJob)request.message, request);
        } else if (request.message instanceof AddUserDailyCast) {
            addUserDailyCast((AddUserDailyCast)request.message, request);
        } else if (request.message instanceof DeleteUserDailyCast) {
            deleteUserDailyCast((DeleteUserDailyCast)request.message, request);
        } else if (request.message instanceof UpdateUserDailyCast) {
            updateUserDailyCast((UpdateUserDailyCast)request.message, request);
        } else if (request.message instanceof AddDailyCast) {
            addDailyCast((AddDailyCast)request.message, request);
        } else if (request.message instanceof DeleteDailyCast) {
            deleteDailyCast((DeleteDailyCast)request.message, request);
        } else if (request.message instanceof UpdateDailyCast) {
            updateDailyCast((UpdateDailyCast)request.message, request);
        } else if (request.message instanceof UpdateUserInfo) {
            updateUserInfo((UpdateUserInfo)request.message, request);
        }
    }

    private void updateSitus(UpdateSitus msg, ServiceRequest request) {
        try {
            if (StringUtils.isEmpty(msg.situs) || StringUtils.isEmpty(msg.userId)) {
                logger.debug("Situs or userId is empty: userId={}", msg.userId);
                return;
            }
            logger.debug("updateSitus: product={}, userId={}, userInfo={}, situs={}, situsGroup={}, location={}",
                msg.product, msg.userId, msg.userInfo, msg.situs, msg.situsGroup, msg.location);
            PushTarget target = stat.pushTargetService.updateSitus(msg.product, msg.userId, msg.userInfo, msg.situs, msg.situsGroup, msg.location);
            PushResult<Long> result = new PushResult<>(0, target.getId());
            sender().tell(new ServiceResponse(result, request, true), self());
        } catch (Exception ex) {
            logger.warn("Update user situs failed: userId={}", msg.userId, ex);
            PushResult<Long> result = new PushResult<>(1);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    private void updateToken(UpdateToken msg, ServiceRequest request) {
        try {
            if (StringUtils.isEmpty(msg.token) || StringUtils.isEmpty(msg.userId)) {
                logger.debug("Token or userId is empty: userId={}", msg.userId);
                return;
            }
            logger.debug("updateToken: product={}, userId={}, token={}, userInfo={}",
                msg.product, msg.userId, msg.token, msg.userInfo);
            PushTarget target = stat.pushTargetService.updateToken(msg.product, msg.userId, msg.userInfo, msg.token, true);
            PushResult<Long> result = new PushResult<>(0, target.getId());
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Update token failed: userId={}", msg.userId, ex);
            PushResult<Long> result = new PushResult<>(1);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    private void updateTokenStatus(UpdateTokenStatus msg, ServiceRequest request) {
        try {
            logger.debug("updateTokenStatus: token={}, actived={}", msg.token, msg.actived);
            boolean n = stat.pushTargetService.updateTokenStatus(msg.token, msg.actived);
            PushResult<Boolean> result = new PushResult<>(0, n);
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Update token's status failed: {}", msg.token, ex);
            PushResult<Boolean> result = new PushResult<>(1, false);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    //---------------------------------------------------------------------------------------------------
    //CastJob
    private void addCastJob(AddCastJob msg, ServiceRequest request) {
        try {
            logger.debug("addCastJob: {}", msg.job.getDescription());
            CastJob job = stat.castJobService.addCastJob(msg.job);
            PushResult<Long> result = new PushResult<>(0, job.getId());
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Add CastJob failed: {}", msg.job.getDescription(), ex);
            PushResult<Long> result = new PushResult<>(1);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    private void addCastJobs(AddCastJobs msg, ServiceRequest request) {
        try {
            logger.debug("addCastJobs: count={}", msg.jobs.size());
            int count = stat.castJobService.addCastJobs(msg.jobs);
            PushResult<Integer> result = new PushResult<>(0, count);
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Add CastJobs failed", ex);
            PushResult<Integer> result = new PushResult<>(1, 0);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    private void updateCastJob(UpdateCastJob msg, ServiceRequest request) {
        try {
            stat.castJobService.updateCastJob(msg.job);
            PushResult<Boolean> result = new PushResult<>(0, true);
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Update CastJob failed: jobId={}", msg.job.getId(), ex);
            PushResult<Boolean> result = new PushResult<>(1, false);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    private void deleteCastJob(DeleteCastJob msg, ServiceRequest request) {
        try {
            stat.castJobService.deleteCastJob(msg.jobId);
            PushResult<Boolean> result = new PushResult<>(0, true);
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Delete CastJob failed: jobId={}", msg.jobId, ex);
            PushResult<Boolean> result = new PushResult<>(1, false);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    private void getCastJobs(GetCastJobs msg, ServiceRequest request) {
        try {
            List<CastJob> jobs = stat.castJobService.getCastJobs(msg.payloadType, msg.page, msg.pageSize);
            PushResult<List<CastJob>> result = new PushResult<>(0, jobs);
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Get CastJobs failed", ex);
            PushResult<List<CastJob>> result = new PushResult<>(1);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    private void getCastJob(GetCastJob msg, ServiceRequest request) {
        try {
            CastJob job = stat.castJobService.getCastJob(msg.jobId);
            PushResult<CastJob> result = new PushResult<>(0, job);
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Get CastJob failed: jobId={}", msg.jobId, ex);
            PushResult<CastJob> result = new PushResult<>(1);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    //---------------------------------------------------------------------------------------------------
    //UserDailyTimer
    private void getUserDailyTimers(GetUserDailyTimers msg, ServiceRequest request) {
        try {
            List<UserDailyTimer> timers = stat.userDailyTimerService.getUserDailyTimers(msg.product, msg.userId);
            PushResult<List<UserDailyTimer>> result = new PushResult<>(0, timers);
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Get UserDailyTimer failed: userId={},product={}", msg.userId, msg.product, ex);
            PushResult<List<UserDailyTimer>> result = new PushResult<>(1);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    private void updateUserDailyTimer(UpdateUserDailyTimer msg, ServiceRequest request) {
        try {
            UserDailyTimer timer = new UserDailyTimer();
            timer.setProduct(msg.product);
            timer.setUserId(msg.userId);
            timer.setMinuteOfDay(msg.minuteOfDay);
            timer.setEnabled(msg.enabled);
            timer.setDays(msg.days);
            timer.setPayloadType(msg.payloadType);
            timer.setTimerId(msg.timerId);
            timer.setPayloadUrl(msg.payloadUrl);
            timer.setPayloadCacheKeys(msg.payloadCacheKeys);
            UserDailyTimer timerNew = stat.userDailyTimerService.updateUserDailyTimer(timer);
            PushResult<Long> result = new PushResult<>(0, timerNew.getId());
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Update UserDailyTimer failed: product={}, userId={}", msg.product, msg.userId, ex);
            PushResult<Long> result = new PushResult<>(1);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    private void updateUserDailyTimerStatus(UpdateUserDailyTimerStatus msg, ServiceRequest request) {
        try {
            boolean ret = stat.userDailyTimerService.updateUserDailyTimerStatus(msg.product, msg.userId, msg.payloadType, msg.timerId, msg.enabled);
            PushResult<Boolean> result = new PushResult<>(0, ret);
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Update UserDailyTimer failed: product={}, userId={}, timerId={}", msg.product, msg.userId, msg.timerId, ex);
            PushResult<Boolean> result = new PushResult<>(1, false);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    private void deleteUserDailyTimer(DeleteUserDailyTimer msg, ServiceRequest request) {
        try {
            boolean ret = stat.userDailyTimerService.deleteUserDailyTimer(msg.product, msg.userId, msg.payloadType, msg.timerId);
            PushResult<Boolean> result = new PushResult<>(0, ret);
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Delete UserDailyTimer failed: product={}, userId={}, timerId={}", msg.product, msg.userId, msg.timerId, ex);
            PushResult<Boolean> result = new PushResult<>(1, false);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    //-----------------------------------------------------------------------
    //DailyCast
    private void addDailyCast(AddDailyCast msg, ServiceRequest request) {
        try {
            DailyCast dailyCast = stat.dailyCastService.add(msg.dailyCast);
            PushResult<Long> result = new PushResult<>(0, dailyCast.getId());
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Add DailyCast failed: {}", msg.dailyCast.getDescription(), ex);
            PushResult<Long> result = new PushResult<>(1);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    private void updateDailyCast(UpdateDailyCast msg, ServiceRequest request) {
        try {
            stat.dailyCastService.updateDailyCast(msg.dailyCast);
            PushResult<Boolean> result = new PushResult<>(0, true);
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Update DailyCast failed: dailyCastId={}", msg.dailyCast.getId(), ex);
            PushResult<Boolean> result = new PushResult<>(1, false);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    private void deleteDailyCast(DeleteDailyCast msg, ServiceRequest request) {
        try {
            boolean n = stat.dailyCastService.deleteDailyCast(msg.product, msg.payloadType);
            PushResult<Boolean> result = new PushResult<>(0, n);
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Delete DailyCast failed: product={}, payloadType={}", msg.product, msg.payloadType, ex);
            PushResult<Boolean> result = new PushResult<>(1, false);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    //---------------------------------------------------------------------------------------
    //UserDailyCast
    private void addUserDailyCast(AddUserDailyCast msg, ServiceRequest request) {
        try {
            UserDailyCast dailyCast = stat.userDailyCastService.add(msg.dailyCast);
            PushResult<Long> result = new PushResult<>(0, dailyCast.getId());
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Add UserDailyCast failed: {}", msg.dailyCast.getDescription(), ex);
            PushResult<Long> result = new PushResult<>(1);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    private void updateUserDailyCast(UpdateUserDailyCast msg, ServiceRequest request) {
        try {
            stat.userDailyCastService.updateDailyCast(msg.dailyCast);
            PushResult<Boolean> result = new PushResult<>(0, true);
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Update UserDailyCast failed: id={}", msg.dailyCast.getId(), ex);
            PushResult<Boolean> result = new PushResult<>(1, false);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

    private void deleteUserDailyCast(DeleteUserDailyCast msg, ServiceRequest request) {
        try {
            boolean n = stat.userDailyCastService.deleteDailyCast(msg.product, msg.payloadType);
            PushResult<Boolean> result = new PushResult<>(0, n);
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Delete UserDailyCast failed: product={}, payloadType={}", msg.product, msg.payloadType, ex);
            PushResult<Boolean> result = new PushResult<>(1, false);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }
    //---------------------------------------------------------------------------------------
    //UserInfo修改接口

    private void updateUserInfo(UpdateUserInfo msg, ServiceRequest request) {
        try {
            PushTarget target = stat.pushTargetService.updateUserInfo(msg.product, msg.userId, msg.userInfo);
            PushResult<Long> result = new PushResult<>(0, target.getId());
            sender().tell(new ServiceResponse(result, request), self());
        } catch (Exception ex) {
            logger.warn("Update UserInfo failed: product={}, userId={}, userInfo={}", msg.product, msg.userId, msg.userInfo, ex);
            PushResult<Long> result = new PushResult<>(1);
            sender().tell(new ServiceResponse(result, request, false), self());
        }
    }

}
