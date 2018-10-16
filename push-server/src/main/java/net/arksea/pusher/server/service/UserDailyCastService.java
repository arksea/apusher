package net.arksea.pusher.server.service;

import net.arksea.pusher.CastType;
import net.arksea.pusher.entity.CastJob;
import net.arksea.pusher.entity.UserDailyCast;
import net.arksea.pusher.server.repository.UserDailyCastDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.List;

/**
 *
 * Created by xiaohaixing on 2017/11/6.
 */
@Component
@Transactional
public class UserDailyCastService {
    private static Logger logger = LogManager.getLogger(UserDailyCastService.class);
    @Autowired
    UserDailyCastDao dailyCastDao;
    @Autowired
    CastJobService castJobService;

    public List<UserDailyCast> getNotCreated(ZonedDateTime date, int page, int pageSize) {
        long epochMill = date.toEpochSecond()*1000;
        Timestamp time = new Timestamp(epochMill);
        return dailyCastDao.getNotCreated(time, new PageRequest(page, pageSize));
    }
    public UserDailyCast updateDailyCast(UserDailyCast d) {
        if (d.getId() == null) {
            throw new IllegalArgumentException("the UserDailyCast's id is null");
        } else {
            UserDailyCast old = dailyCastDao.findOne(d.getId());
            if (old == null) {
                throw new IllegalArgumentException("the job not exists: "+d.getId());
            } else { //不允许客户端修改Key
                d.setProduct(old.getProduct());
                d.setPayloadType(old.getPayloadType());
                return dailyCastDao.save(d);
            }
        }
    }
    public boolean deleteDailyCast(String product, String payloadType) {
        int n = dailyCastDao.deleteByProductAndPayloadType(product, payloadType);
        if (n > 1) {
            logger.fatal("断言失败： 按定义最多只应删除一条记录，请排查数据或逻辑。product={},payloadType={}", product, payloadType);
        }
        return n >= 1;
    }
    public void addCastJob(ZonedDateTime jobStartTime, UserDailyCast cast) {
        CastJob job = makeCastJob(jobStartTime, cast);
        castJobService.addCastJob(job);
        cast.setLastCreated(new Timestamp(jobStartTime.toEpochSecond()*1000));
        dailyCastDao.save(cast);
    }


    private CastJob makeCastJob(ZonedDateTime jobStartTime, UserDailyCast cast) {
        CastJob job = new CastJob();
        job.setDescription(cast.getDescription());
        job.setProduct(cast.getProduct());
        int minuteOfDay = jobStartTime.getHour()*60 + jobStartTime.getMinute();
        job.setCastTarget(Integer.toString(minuteOfDay));
        job.setCastType(CastType.USER_DAILY_TIMER);
        job.setTestTarget(cast.getTestTarget());
        job.setEnabled(true);
        job.setUserFilter(cast.getUserFilter());
        job.setPayloadType(cast.getPayloadType());
        job.setPayload(cast.getPayload());
        long startTime = jobStartTime.toEpochSecond()*1000;
        job.setStartTime(new Timestamp(startTime));
        job.setExpiredTime(new Timestamp(startTime+3600000));
        return job;
    }

    public UserDailyCast add(UserDailyCast cast) {
        if (cast.getId() == null) {
            return dailyCastDao.save(cast);
        } else {
            throw new IllegalArgumentException("id can't be specified when add a UserDailyCast");
        }
    }

    public UserDailyCast update(UserDailyCast job) {
        if (job.getId() == null) {
            throw new IllegalArgumentException("the UserDailyCast id is null");
        } else {
            return dailyCastDao.save(job);
        }
    }

    public void delete(long id) {
        dailyCastDao.delete(id);
    }

    public void updateEnabled(long id, boolean enabled) {
        dailyCastDao.updateEnabled(id, enabled);
    }
}
