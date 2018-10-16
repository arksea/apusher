package net.arksea.pusher.server.service;

import akka.dispatch.Mapper;
import net.arksea.pusher.server.Partition;
import net.arksea.pusher.entity.PushTarget;
import net.arksea.pusher.entity.UserDailyTimer;
import net.arksea.pusher.server.repository.UserDailyTimerDao;
import net.arksea.acache.CacheService;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import scala.concurrent.Future;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2018/2/5.
 */
@Component
@Transactional
public class UserDailyTimerService {
    private static Logger logger = LogManager.getLogger(UserDailyTimerService.class);
    private static final int PUSH_TARGET_PAGE_SIZE = 200;

    @Autowired
    UserDailyTimerDao userDailyTimerDao;

    @Autowired
    PayloadService payloadService;

    @Autowired
    CacheService<PushTargetCacheFactory.PushTargetKey, PushTarget> pushTargetCacheService;

    public List<UserDailyTimer> getUserDailyTimers(String product, String userId) {
        return userDailyTimerDao.findByUserIdAndProduct(userId, product);
    }

    public UserDailyTimer updateUserDailyTimer(UserDailyTimer timer) {
        List<UserDailyTimer> list = userDailyTimerDao.findByKey(timer.getUserId(),timer.getProduct(),timer.getPayloadType(),timer.getTimerId());
        if (list.isEmpty()) {
            timer.setPartitions(Partition.makeUserPartition(timer.getUserId()));
            timer.setZoneOffset(8);
            return userDailyTimerDao.save(timer);
        } else {
            UserDailyTimer timerOld = list.get(0);
            //只有这几个字段允许修改，其他创建后不能修改
            timerOld.setMinuteOfDay(timer.getMinuteOfDay());
            timerOld.setDays(timer.getDays());
            timerOld.setPayloadUrl(timer.getPayloadUrl());
            timerOld.setEnabled(timer.isEnabled());
            return userDailyTimerDao.save(timerOld);
        }
    }

    /**
     *
     * @param userId
     * @param product
     * @param payloadType
     * @param enabled
     * @return  返回true表示更新成功，返回false表示没有查询到指定的timer
     */
    public boolean updateUserDailyTimerStatus(final String product, final String userId,final String payloadType,long timerId, boolean enabled) {
        logger.debug("updateUserDailyTimerStatus: pid={},uid={},ptype={},enabled={}", product, userId, payloadType, enabled);
        int n = userDailyTimerDao.updateTimerStatus(userId, product, payloadType, timerId, enabled);
        if (n > 1) {
            logger.fatal("断言失败： 按定义最多只应修改一条记录，请排查数据或逻辑。{},{},{},{}", product, userId, payloadType, enabled);
        }
        return n >= 1;
    }

    public boolean deleteUserDailyTimer(final String product,final String userId,final String payloadType,long timerId) {
        long start = System.currentTimeMillis();
        int n = userDailyTimerDao.setTimerDeleted(userId, product, payloadType, timerId);
        logger.info("set UserDailyTimer deleted=true: pid={},type={},timerId={},uid={}; use {} ms",
            product, payloadType, timerId, userId, System.currentTimeMillis() - start);
        if (n > 1) {
            logger.fatal("断言失败： 按定义最多只应删除一条记录，请排查数据或逻辑。{},{},{},{}", product, userId, payloadType);
        }
        return n >= 1;
    }


    public int deleteByProductAndUserId(String product, String userId) {
        long start = System.currentTimeMillis();
        int n = userDailyTimerDao.setTimerDeleted(userId, product);
        logger.debug("set UserDailyTimer deleted=true: pid={},uid={}; use {} ms",
            product, userId, System.currentTimeMillis() - start);
        return n;
    }

    public boolean existsTimer(String product, int minuteOfDay, String payloadType) {
        return userDailyTimerDao.findByMinuteOfDay(product, minuteOfDay, payloadType, new PageRequest(0, 1)).size() > 0;
    }

    public long getTimerCount(String product, int minuteOfDay, String payloadType) {
        return userDailyTimerDao.countByMinuteOfDayAndPayloadType(product, minuteOfDay, payloadType);
    }

    public Future<List<PushTarget>> findPartitionTop(int partition, String product,
                                                     int minuteOfDay, String payloadType, String fromUserId,
                                                     Map<String,String> payloadCache) {
        List<UserDailyTimer> timerList;
        if (fromUserId == null) {
            timerList = userDailyTimerDao.findByPartition(partition, product, minuteOfDay, payloadType,
                new PageRequest(0, PUSH_TARGET_PAGE_SIZE));
        } else {
            timerList = userDailyTimerDao.findByPartition(partition, product, minuteOfDay, payloadType, fromUserId,
                new PageRequest(0, PUSH_TARGET_PAGE_SIZE));
        }
        if (timerList == null) {
            return null;
        }

        List<Future<PushTarget>> targets = new LinkedList<>();
        String dayOfWeek = Integer.toString(LocalDate.now().getDayOfWeek().getValue());
        for (UserDailyTimer timer : timerList) {
            if (!StringUtils.isEmpty(timer.getDays()) && timer.getDays().indexOf(dayOfWeek)==-1) {
                continue;
            }
            PushTargetCacheFactory.PushTargetKey key = new PushTargetCacheFactory.PushTargetKey(timer.getProduct(),timer.getUserId());
            Future<PushTarget> s = pushTargetCacheService.get(key).map(
                new Mapper<PushTarget, PushTarget>() {
                    public PushTarget apply(PushTarget it) {
                        try {
                            //clone()是必须的操作，否则cache中的pushTarget对象将可能被修改
                            return it == null ? null : it.clone();
                        } catch (CloneNotSupportedException e) {
                            throw new RuntimeException("clone PushTarget failed", e);
                        }
                    }
                }, pushTargetCacheService.dispatcher);
            Future<PushTarget> t = payloadService.fillPayload(s, timer.getPayloadUrl(), timer.getPayloadCacheKeys(), payloadCache);
            targets.add(t);
        }
        return payloadService.sequence(targets);
    }

    public int cleanDeleted() {
        return userDailyTimerDao.cleanDeleted();
    }

    public int cleanDeletedByPartition(int partition) {
        return userDailyTimerDao.cleanDeletedByPartition(partition);
    }
}
