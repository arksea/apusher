package net.arksea.pusher;

import akka.actor.ActorSystem;
import net.arksea.dsf.client.Client;
import net.arksea.dsf.client.route.RouteStrategy;
import net.arksea.dsf.register.RegisterClient;
import net.arksea.pusher.entity.CastJob;
import net.arksea.pusher.entity.DailyCast;
import net.arksea.pusher.entity.UserDailyCast;
import net.arksea.pusher.entity.UserDailyTimer;
import net.arksea.pusher.request.*;
import scala.concurrent.Future;

import java.util.List;

import static akka.japi.Util.classTag;
/**
 *
 * Created by xiaohaixing on 2017/3/1.
 */
public class PushService {

    public final ActorSystem system;
    private final int timeout;
    private final Client dsfClient;

    public PushService(final RegisterClient register, final String serviceName, final int timeout) {
        this.dsfClient = register.subscribe(serviceName, RouteStrategy.HOT_STANDBY);
        this.system = dsfClient.system;
        this.timeout = timeout;
    }

    @SuppressWarnings("unchecked")
    private <T> Future<PushResult<T>> request(final PushRequest request) {
        return dsfClient.request(request, timeout).mapTo(classTag((Class<PushResult<T>>) (Class<?>)PushResult.class));
    }

    //--------------------------------------------------------------------------------------
    //CastJob接口

    /**
     *
     * @param job
     * @return 成功返回 CastJob.id
     */
    public Future<PushResult<Long>> addCastJob(CastJob job) {
        return request(new AddCastJob(job));
    }

    /**
     *
     * @param jobs
     * @return 返回成功添加的个数
     */
    public Future<PushResult<Integer>> addCastJobs(List<CastJob> jobs) {
        return request(new AddCastJobs(jobs));
    }

    public Future<PushResult<Boolean>> updateCastJob(CastJob job) {
        return request(new UpdateCastJob(job));
    }

    public Future<PushResult<Boolean>> deleteCastJob(long jobId) {
        return request(new DeleteCastJob(jobId));
    }

    public Future<PushResult<List<CastJob>>> getCastJobs(String payloadType, int page, int pageSize) {
        return request(new GetCastJobs(payloadType, page, pageSize));
    }

    public Future<PushResult<List<CastJob>>> getCastJobs(int page, int pageSize) {
        return request(new GetCastJobs(page, pageSize));
    }

    public Future<PushResult<CastJob>> getCastJob(long jobId) {
        return request(new GetCastJob(jobId));
    }
    //---------------------------------------------------------------------------------------
    //UserDailyTimer接口
    /**
     * 新建或更新用户每日定时器
     * @param product
     * @param userId
     * @param payloadType
     * @param minuteOfDay
     * @param payloadUrl
     * @param enabled
     * @return 成功返回UserDailyTimer.id
     */
    public Future<PushResult<Long>> updateUserDailyTimer(String product, String userId,  String payloadType, long timerId,
                                                              int zoneOffset,int minuteOfDay,String payloadUrl, String payloadCacheKeys,
                                                              String days, boolean enabled) {
        return request(new UpdateUserDailyTimer(product, userId,  payloadType, timerId, zoneOffset, minuteOfDay,payloadUrl, payloadCacheKeys, days, enabled));
    }

    /**
     * 修改用户每日定时器状态
     * @param product
     * @param userId
     * @param payloadType
     * @param enabled
     * @return 返回true表示更新成功，返回false表示没有查询到指定的timer
     */
    public Future<PushResult<Boolean>> updateUserDailyTimerStatus(String product, String userId,  String payloadType, long timerId, boolean enabled) {
        return request(new UpdateUserDailyTimerStatus(product, userId,  payloadType, timerId, enabled));
    }

    /**
     *
     * @param product
     * @param userId
     * @param payloadType
     * @param timerId
     * @return 返回true表示删除成功，返回false表示没有查询到指定的timer
     */
    public Future<PushResult<Boolean>> deleteUserDailyTimer(String product, String userId,  String payloadType, long timerId) {
        return request(new DeleteUserDailyTimer(product, userId,  payloadType, timerId));
    }

    public Future<PushResult<List<UserDailyTimer>>> getUserDailyTimers(String product, String userId) {
        return request(new GetUserDailyTimers(userId,product));
    }
    //---------------------------------------------------------------------------------------
    //UserDailyCast接口

    /**
     *
     * @param dailyCast
     * @return 成功返回UserDailyCast.id
     */
    public Future<PushResult<Long>> addUserDailyCast(UserDailyCast dailyCast) {
        return request(new AddUserDailyCast(dailyCast));
    }

    public Future<PushResult<Boolean>> updateUserDailyCast(UserDailyCast dailyCast) {
        return request(new UpdateUserDailyCast(dailyCast));
    }

    /**
     *
     * @param product
     * @param payloadType
     * @return 返回1表示删除成功，返回0表示没有找到记录
     */
    public Future<PushResult<Boolean>> deleteUserDailyCast(String product, String payloadType) {
        return request(new DeleteUserDailyCast(product, payloadType));
    }


    //---------------------------------------------------------------------------------------
    //DailyCast接口

    /**
     *
     * @param dailyCast
     * @return 成功返回DailyCast.id
     */
    public Future<PushResult<Long>> addDailyCast(DailyCast dailyCast) {
        return request(new AddDailyCast(dailyCast));
    }
    /**
     *
     * @param dailyCast
     * @return 成功返回DailyCast.id
     */
    public Future<PushResult<Boolean>> updateDailyCast(DailyCast dailyCast) {
        return request(new UpdateDailyCast(dailyCast));
    }

    /**
     *
     * @param product
     * @param payloadType
     * @return 返回true表示删除成功，返回false表示没有找到记录
     */
    public Future<PushResult<Boolean>> deleteDailyCast(String product, String payloadType) {
        return request(new DeleteDailyCast(product, payloadType));
    }

    //---------------------------------------------------------------------------------------
    //UserInfo接口

    /**
     * 修改指定PushTarget的UserInfo值，当记录不存在时将新建一条
     * @param prodcut  产品
     * @param userId   用户ID
     * @param userInfo Json格式的用户扩展信息
     * @return 成功返回PushTarget.id
     */
    public Future<PushResult<Long>> updateUserInfo(String prodcut, String userId, String userInfo) {
        return request(new UpdateUserInfo(prodcut, userId, userInfo));
    }
    public Future<Long> updateUserInfoField(String prodcut, String userId, String name, Object value) {
        //todo
        throw new UnsupportedOperationException("功能未实现");
    }

    public Future<PushResult<String>> getUserInfo(String product, String userId) {
        return request(new GetUserInfo(product, userId));
    }
    /**
     *
     * @param prodcut
     * @param userId
     * @param situs
     * @param situsGroup
     * @param location
     * @param userInfo
     * @return 成功返回PushTarget.id
     */
    public Future<PushResult<Long>> updateSitus(String prodcut, String userId, String situs,
                                                     String situsGroup, String location, String userInfo) {
        return request(new UpdateSitus(prodcut, userId, situs, situsGroup, location, userInfo));
    }

    /**
     *
     * @param prodcut
     * @param userId
     * @param token
     * @param userInfo
     * @return 成功返回PushTarget.id
     */
    public Future<PushResult<Long>> updateToken(String prodcut, String userId, String token, String userInfo) {
        return request(new UpdateToken(prodcut, userId, token, userInfo));
    }

    /**
     * @param token
     * @param actived
     * @return 返回true表示更新成功，返回false表示没有找到记录
     */
    public Future<PushResult<Boolean>> updateTokenStatus(String token, boolean actived) {
        return request(new UpdateTokenStatus(token, actived));
    }

    /**
     * @param userId
     * @param actived
     * @return 返回true表示更新成功，返回false表示没有找到记录
     */
    public Future<PushResult<Boolean>> updateTokenStatusByUserId(String userId, boolean actived) {
        return request(new UpdateTokenStatusByUID(userId, actived));
    }
}
