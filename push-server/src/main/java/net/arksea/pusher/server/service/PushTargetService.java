package net.arksea.pusher.server.service;

import akka.dispatch.Mapper;
import net.arksea.pusher.server.Partition;
import net.arksea.pusher.entity.PushTarget;
import net.arksea.pusher.server.repository.PushTargetDao;
import net.arksea.acache.CacheAsker;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2018/2/5.
 */
@Component
@Transactional
public class PushTargetService {

    private final static Logger logger = LogManager.getLogger(PushTargetService.class);
    //对于内容没有修改的记录，只在最后更新时间超过1天的记录才更新时间，防止不必要的写入操作
    //因此这里的设置的值(例如1天)必须小于（最好远小于）删除记录的过期时间（例如60天）
    private final static long MIN_UPDATE_PERIOD = 3600_000 * 24 * 1;
    @Autowired
    private PushTargetDao pushTargetDao;

    @Value("${push.pushTarget.queryPageSize:1024}")
    int queryPageSize;

    @Value("${push.pushTarget.deleteRepeatedRecord:true}")
    boolean deleteRepeatedRecord;

    @Autowired
    UserDailyTimerService userDailyTimerService;

    @Autowired
    CacheAsker<PushTargetCacheFactory.PushTargetKey, PushTarget> pushTargetCacheService;

    private PushTarget savePushTarget(PushTarget pt) {
        pushTargetCacheService.markDirty(new PushTargetCacheFactory.PushTargetKey(pt.getProduct(), pt.getUserId()));
        return pushTargetDao.save(pt);
    }

    public long countByPartitionAndProduct(int partitions, String product) {
        return pushTargetDao.countByPartitionAndProduct(partitions, product);
    }

    public long countBySitusAndProduct(String situs, String product) {
        return pushTargetDao.countBySitusAndProduct(situs, product);
    }

    public Future<PushTarget> getPushTarget(final String product, final String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("UserId is blank: userId=" + userId);
        }
        PushTargetCacheFactory.PushTargetKey key = new PushTargetCacheFactory.PushTargetKey(product,userId);
        return pushTargetCacheService.get(key).map(
            new Mapper<PushTarget, PushTarget>() {
                public PushTarget apply(PushTarget it) {
                    try {
                        //clone()是必须的操作，否则cache中的pushTarget对象将可能被修改
                        return  it == null ? null : it.clone();
                    } catch (CloneNotSupportedException e) {
                        throw new RuntimeException("clone PushTarget failed", e);
                    }
                }
            }, pushTargetCacheService.dispatcher);
    }

    private PushTarget deleteRepeatedToken(String product, String token, PushTarget def) {
        //容错措施（pushTarget表可能非常大，为了提高性能，没有建立主键外的唯一性索引，容易因逻辑错误引起数据重复），
        //防止特殊情况下UserId变化，引起两个UserId拥有同一个Token造成的重复推送消息问题
        //（前提是推送平台Token是全局唯一的，当前APNS等主要的推送平台都符合此前提）
        PushTarget pd = def;
        if (deleteRepeatedRecord) {
            List<PushTarget> list = pushTargetDao.findByProductAndToken(product, token);
            if (!list.isEmpty()) {
                pd = list.get(0);
                for (int i = 1; i < list.size(); ++i) {
                    PushTarget p = list.get(i);
                    pushTargetDao.delete(p);
                    logger.error("删除重复Token：id={}, product={}, userId={}, token={}, userInfo={}", p.getId(), product, p.getUserId(), token, p.getUserInfo());
                }
            }
        }
        return pd;
    }

    public PushTarget updateToken(final String product, final String userId, final String userInfo, final String token, final boolean tokenActived) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(token)) {
            throw new IllegalArgumentException("UserId or Token is blank: userId=" + userId + ", token=" + token);
        }
        List<PushTarget> list = pushTargetDao.findByProductAndUserId(product, userId);
        if (list.isEmpty()) {
            PushTarget pd = deleteRepeatedToken(product, token, new PushTarget());
            pd.setProduct(product);
            pd.setUserId(userId);
            pd.setToken(token);
            pd.setTokenActived(tokenActived);
            pd.setPartitions(Partition.makeUserPartition(userId));
            if (userInfo != null) {
                pd.setUserInfo(userInfo);
            }
            pd.setLastUpdate(new Timestamp(System.currentTimeMillis()));
            return savePushTarget(pd);
        } else {
            if (deleteRepeatedRecord && list.size() > 1) {
                for (int i=1;i<list.size();++i) {
                    PushTarget p = list.get(i);
                    pushTargetDao.delete(p);
                    logger.error("删除重复UserId：id={}, product={}, userId={}", p.getId(), product, p.getUserId());
                }
            }
            PushTarget pd = deleteRepeatedToken(product, token, list.get(0));
            if ( needUpdate(pd.getLastUpdate())
                || changed(userInfo, pd.getUserInfo())
                || changed(token, pd.getToken())
                || changed(userId, pd.getUserId())
                || tokenActived != pd.isTokenActived()
            ) {
                pd.setToken(token);
                pd.setUserId(userId);
                pd.setTokenActived(tokenActived);
                if (userInfo != null) {
                    pd.setUserInfo(userInfo);
                }
                pd.setLastUpdate(new Timestamp(System.currentTimeMillis()));
                return savePushTarget(pd);
            } else {
                return pd;
            }
        }
    }

    public PushTarget updateUserInfo(String product, String userId, String userInfo) {
        List<PushTarget> list = pushTargetDao.findByProductAndUserId(product, userId);
        if (list.isEmpty()) {
            PushTarget pd = new PushTarget();
            pd.setProduct(product);
            pd.setUserId(userId);
            pd.setTokenActived(false);
            pd.setPartitions(Partition.makeUserPartition(userId));
            pd.setUserInfo(userInfo);
            pd.setLastUpdate(new Timestamp(System.currentTimeMillis()));
            return savePushTarget(pd);
        } else {
            if (deleteRepeatedRecord && list.size() > 1) {
                for (int i=1;i<list.size();++i) {
                    PushTarget p = list.get(i);
                    pushTargetDao.delete(p);
                    logger.error("删除重复UserId：id={}, product={}, userId={}", p.getId(), product, p.getUserId());
                }
            }
            PushTarget pd = list.get(0);
            if (needUpdate(pd.getLastUpdate()) || changed(userInfo, pd.getUserInfo())) {
                pd.setUserInfo(userInfo);
                pd.setLastUpdate(new Timestamp(System.currentTimeMillis()));
                return savePushTarget(pd);
            } else {
                return pd;
            }
        }
    }
    /**
     *
     * @param token
     * @param tokenActived
     * @return 返回1表示更新成功，返回0表示没有查询到指定的token
     */
    public boolean updateTokenStatus(final String token,final Boolean tokenActived) {
        int n = pushTargetDao.updateTokenStatus(token, tokenActived);
        //取消此处的断言检查：与IOS的token不同，百度的token（ChannelID）是设备唯一，所以有可能出现一台设备装了两个不同ProductId的App后它们上报的token是相同的
        //if (n > 1) {
        //    logger.fatal("断言失败： 按定义最多只应更新一条记录，请排查数据或逻辑。token={}, actived={}", token, tokenActived);
        //}
        return n >= 1;
    }

    public PushTarget updateSitus(final String product, final String userId, final String userInfo, final String situs, final String situsGroup, final String location) {
        List<PushTarget> list = pushTargetDao.findByProductAndUserId(product, userId);
        if (list.isEmpty()) {
            PushTarget pd = new PushTarget();
            pd.setProduct(product);
            pd.setUserId(userId);
            pd.setSitus(situs);
            pd.setSitusGroup(situsGroup);
            pd.setLocation(location);
            pd.setPartitions(Partition.makeUserPartition(userId));
            if (userInfo != null) {
                pd.setUserInfo(userInfo);
            }
            pd.setLastUpdate(new Timestamp(System.currentTimeMillis()));
            return savePushTarget(pd);
        } else {
            if (deleteRepeatedRecord && list.size() > 1) {
                for (int i=1;i<list.size();++i) {
                    PushTarget p = list.get(i);
                    pushTargetDao.delete(p);
                    logger.error("删除重复UserId：id={}, product={}, userId={}", p.getId(), product, p.getUserId());
                }
            }
            PushTarget pd = list.get(0);
            if ( needUpdate(pd.getLastUpdate())
                 || changed(situs,pd.getSitus())
                 || changed(situsGroup,pd.getSitusGroup())
                 || changed(userInfo, pd.getUserInfo())
                 || changed(location, pd.getLocation())
               ) {
                pd.setSitus(situs);
                pd.setSitusGroup(situsGroup);
                pd.setLocation(location);
                if (userInfo != null) {
                    pd.setUserInfo(userInfo);
                }
                pd.setLastUpdate(new Timestamp(System.currentTimeMillis()));
                return savePushTarget(pd);
            } else {
                return pd;
            }
        }
    }

    private boolean changed(String o1, String o2) {
        boolean o1Empty = StringUtils.isEmpty(o1);
        boolean o2Empty = StringUtils.isEmpty(o2);
        if (o1Empty || o2Empty) {
            return o1Empty != o2Empty;
        } else {
            return !o1.equals(o2);
        }
    }

    private boolean needUpdate(Timestamp t) {
        long now = System.currentTimeMillis();
        return t == null || now - t.getTime() > MIN_UPDATE_PERIOD;
    }

    public List<PushTarget> findPartitionTop(int partition, String product, String fromUserId) {
        if (fromUserId == null) {
            return pushTargetDao.findByPartitionAndProduct(partition, product, new PageRequest(0, queryPageSize));
        } else {
            return pushTargetDao.findByPartitionAndProduct(partition, product, fromUserId, new PageRequest(0, queryPageSize));
        }
    }

    public List<PushTarget> findSitusTop(String product, String fromUserId, String situs) {
        if (fromUserId == null) {
            return pushTargetDao.findByProductAndSitus(product, situs, new PageRequest(0, queryPageSize));
        } else {
            return pushTargetDao.findByProductAndSitus(product, fromUserId, situs, new PageRequest(0, queryPageSize));
        }
    }

    public List<PushTarget> findSitusGroupTop(int partition, String product, String fromUserId, List<String> situsGroups) {
        if (fromUserId == null) {
            return pushTargetDao.findByPartitionAndProductAndSitusGroup(partition, product, situsGroups, new PageRequest(0, queryPageSize));
        } else {
            return pushTargetDao.findByPartitionAndProductAndSitusGroup(partition, product, fromUserId, situsGroups, new PageRequest(0, queryPageSize));
        }
    }

    public List<PushTarget> findByProductAndUserId(final String product, final String userId) {
        return pushTargetDao.findByProductAndUserId(product, userId);
    }

    public void cleanBefore(int partition, Timestamp before) {
        int page = 0;
        int count = 0;
        long start = System.currentTimeMillis();
        List<PushTarget> targets = pushTargetDao.findLastUpdateBefore(partition, before, new PageRequest(page, queryPageSize));
        logger.debug("partition {} lastUpdateBefore {} count={}", partition, before, targets.size());
        while(!targets.isEmpty()) {
            for (PushTarget t: targets) {
                int n = userDailyTimerService.deleteByProductAndUserId(t.getProduct(), t.getUserId());
                count += n;
            }
            targets = pushTargetDao.findLastUpdateBefore(partition, before, new PageRequest(++page, queryPageSize));
        }
        if (count > 0) {
            long endTime = System.currentTimeMillis();
            logger.info("set UserDailyTimers deleted that PushTarget.lastUpdate before {}, count={}, targets.size={}, partition={}, use {} ms",
                before, count, targets.size(), partition, endTime - start);
        }
    }

    public void deleteBefore(Timestamp before) {
        long start = System.currentTimeMillis();
        int count = userDailyTimerService.cleanDeleted();
        long endTime = System.currentTimeMillis();
        logger.info("delete UserDailyTimers, count={}, use {} ms",
                count, endTime - start);
        start = endTime;
        count = pushTargetDao.deleteBefore(before);
        endTime = System.currentTimeMillis();
        logger.info("delete PushTargets that lastUpdate before {}, count={}, use {} ms",
                before, count, endTime - start);
    }

    public void deleteBeforeByPartiton(int partition, Timestamp before) {
        long start = System.currentTimeMillis();
        int count = userDailyTimerService.cleanDeletedByPartition(partition);
        long endTime = System.currentTimeMillis();
        if (count > 0) {
            logger.info("delete UserDailyTimers, partition={}, count={}, use {} ms",
                partition, count, endTime - start);
        } else {
            logger.debug("delete UserDailyTimers, partition={}, count={}, use {} ms",
                partition, count, endTime - start);
        }
        start = endTime;
        count = pushTargetDao.deleteBeforeByPartiton(partition, before);
        endTime = System.currentTimeMillis();
        if (count > 0) {
            logger.info("delete PushTargets that lastUpdate before {}, partition={}, count={}, use {} ms",
                before, partition, count, endTime - start);
        } else {
            logger.debug("delete PushTargets that lastUpdate before {}, partition={}, count={}, use {} ms",
                before, partition, count, endTime - start);
        }
    }
}
