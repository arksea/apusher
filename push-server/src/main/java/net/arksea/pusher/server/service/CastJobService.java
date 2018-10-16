package net.arksea.pusher.server.service;

import net.arksea.pusher.CastType;
import net.arksea.pusher.server.cast.ITargetSource;
import net.arksea.pusher.server.cast.TargetSourceFactory;
import net.arksea.pusher.entity.CastJob;
import net.arksea.pusher.server.repository.CastJobDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;

/**
 *
 * Created by xiaohaixing on 2017/11/6.
 */
@Component
@Transactional
public class CastJobService {
    private static Logger logger = LogManager.getLogger(CastJobService.class);

    private static final long BROWSE_FROM_MILLIS = 3600L*24*30*1000; //只查看30天内的任务
    private static final int MAX_TOP_JOBS = 100;

    private static final long ONTIME_FROM_MILLIS = 3600L*24*3*1000; //调度3天内未过期的任务

    @Autowired
    CastJobDao castJobDao;

    @Autowired
    TargetSourceFactory targetSourceFactory;

    public CastJob addCastJob(CastJob job) {
        if (job.getId() == null) {
            ITargetSource source = targetSourceFactory.createTargetSource(job);
            if (source.hasPushTargets(job)) {
                //不允许客户端设置推送计数,与完成时间
                job.setFinishedTime(null);
                job.setAllCount(null);
                job.setFailedCount(0);
                job.setRetryCount(0);
                job.setSucceedCount(null);
                if (job.getCastType() == CastType.BROAD && StringUtils.isEmpty(job.getCastTarget())) {
                    job.setCastTarget("all");
                }
                return castJobDao.save(job);
            } else {
                return job;
            }
        } else {
            throw new IllegalArgumentException("the job id can't be specified");
        }
    }

    public int addCastJobs(Iterable<CastJob> jobs) {
        int count = 0;
        Iterator<CastJob> it = jobs.iterator();
        while(it.hasNext()) {
            CastJob job = it.next();
            ITargetSource source = targetSourceFactory.createTargetSource(job);
            if (source.hasPushTargets(job)) {
                //不允许客户端设置推送计数,与完成时间
                job.setFinishedTime(null);
                job.setAllCount(null);
                job.setFailedCount(0);
                job.setRetryCount(0);
                job.setSucceedCount(null);
                if (job.getCastType() == CastType.BROAD && StringUtils.isEmpty(job.getCastTarget())) {
                    job.setCastTarget("all");
                }
                castJobDao.save(job);
                ++count;
            }
        }
        return count;
    }

    public CastJob updateCastJob(CastJob job) {
        if (job.getId() == null) {
            throw new IllegalArgumentException("the job id is null");
        } else {
            CastJob old = castJobDao.findOne(job.getId());
            if (old == null) {
                throw new IllegalArgumentException("the job not exists: "+job.getId());
            } else if (old.getSucceedCount() != null || isUpComingJob(old)) {
                throw new IllegalArgumentException("Can't modify a started or up coming job: "+job.getId());
            } else { //不允许客户端设置推送计数
                job.setAllCount(old.getAllCount());
                job.setFailedCount(old.getFailedCount());
                job.setSucceedCount(old.getSucceedCount());
                job.setRetryCount(old.getRetryCount());
                if (job.getCastType() == CastType.BROAD && StringUtils.isEmpty(job.getCastTarget())) {
                    job.setCastTarget("all");
                }
                return castJobDao.save(job);
            }
        }
    }

    public CastJob saveCastJobByServer(CastJob job) {
        return castJobDao.save(job);
    }

    /**
     * 启动时间在5分钟内认为是即将启动的任务
     * @param job
     * @return
     */
    private boolean isUpComingJob(CastJob job) {
        long now = System.currentTimeMillis();
        long start = job.getStartTime().getTime();
        return start - now < 300000;
    }

    public List<CastJob> getCastJobs(final String payloadType, final int page, final int pageSize) {
        long t = System.currentTimeMillis() - BROWSE_FROM_MILLIS;
        Timestamp from = new Timestamp(t);
        if (payloadType == null) {
            return castJobDao.getFrom(from, new PageRequest(page, pageSize));
        } else {
            return castJobDao.getByPayloadtypeFrom(payloadType, from, new PageRequest(page, pageSize));
        }
    }

    public CastJob getCastJob(long jobId) {
        return castJobDao.findOne(jobId);
    }

    public List<CastJob> getOnTimeCastJobs() {
        long now = System.currentTimeMillis();
        Timestamp from = new Timestamp(now - ONTIME_FROM_MILLIS);
        Timestamp to = new Timestamp(now);
        List<CastJob> jobs = castJobDao.getOnTimeBetween(from, to, new PageRequest(0, MAX_TOP_JOBS));
        logger.debug("getOnTimeCastJobs, count={}, from={}, to={}", jobs.size(), from, to);
        return jobs;
    }

    public void deleteCastJob(long id) {
        castJobDao.delete(id);
    }

    public void resetRunningCastJob() {
        castJobDao.resetRunningStatus();
    }

    public int deleteOldCastJob(CastType castType, Timestamp time) {
        return castJobDao.deleteOldCastJob(castType, time);
    }
}
