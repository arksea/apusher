package net.arksea.pusher.server.service;

import net.arksea.pusher.CastType;
import net.arksea.pusher.entity.CastJob;
import net.arksea.pusher.entity.DailyCast;
import net.arksea.pusher.entity.PushTarget;
import net.arksea.pusher.server.repository.DailyCastDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import scala.concurrent.Future;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2017/11/6.
 */
@Component
@Transactional
public class DailyCastService {
    private static Logger logger = LogManager.getLogger(DailyCastService.class);
    @Autowired
    DailyCastDao dailyCastDao;
    @Autowired
    CastJobService castJobService;
    @Autowired
    PayloadService payloadService;
    @Autowired
    PushTargetService pushTargetService;

    public List<DailyCast> getNotCreated(ZonedDateTime date, int page, int pageSize) {
        long epochMill = date.toEpochSecond()*1000;
        Timestamp time = new Timestamp(epochMill);
        return dailyCastDao.getNotCreated(time, new PageRequest(page, pageSize));
    }
    public DailyCast updateDailyCast(DailyCast d) {
        if (d.getId() == null) {
            throw new IllegalArgumentException("the DailyCast's id is null");
        } else {
            DailyCast old = dailyCastDao.findOne(d.getId());
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

    public void addCastJob(ZonedDateTime startOfToday, DailyCast cast, boolean batchDailyCast) {
        CastJob job = makeCastJob(startOfToday, cast, batchDailyCast);
        castJobService.addCastJob(job);
        cast.setLastCreated(new Timestamp(startOfToday.toEpochSecond()*1000));
        dailyCastDao.save(cast);
    }

    private CastJob makeCastJob(ZonedDateTime startOfToday, DailyCast cast, boolean batchDailyCast) {
        CastJob job = new CastJob();
        job.setDescription(cast.getDescription());
        job.setProduct(cast.getProduct());
        job.setCastTarget(cast.getId().toString());
        job.setCastType(batchDailyCast ? CastType.BATCH_DAILY : CastType.USER_DAILY);
        job.setTestTarget(cast.getTestTarget());
        job.setEnabled(true);
        job.setUserFilter(cast.getUserFilter());
        job.setPayloadType(cast.getPayloadType());
        job.setPayload("");
        long startTime = startOfToday.toEpochSecond()*1000 + cast.getMinuteOfDay()*60_000;
        job.setStartTime(new Timestamp(startTime));
        job.setExpiredTime(new Timestamp(startTime+3600000));
        return job;
    }

    public DailyCast add(DailyCast cast) {
        if (cast.getId() == null) {
            return dailyCastDao.save(cast);
        } else {
            throw new IllegalArgumentException("id can't be specified when add a DailyCast");
        }
    }

    public DailyCast update(DailyCast job) {
        if (job.getId() == null) {
            throw new IllegalArgumentException("the DailyCast id is null");
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


    public Future<List<PushTarget>> findPartitionTop(int partition,
                                                     String product,
                                                     long dailyCastId,
                                                     String fromUserId,
                                                     Map<String,String> payloadCache) {
        DailyCast dailyCast = dailyCastDao.findOne(dailyCastId);
        if (dailyCast == null) {
            logger.warn("the DailyCast not exists: {}", dailyCastId);
            return null;
        }
        List<PushTarget> list = pushTargetService.findPartitionTop(partition, product, fromUserId);
        List<Future<PushTarget>> targets = new LinkedList<>();
        for (PushTarget target : list) {
            Future<PushTarget> f = payloadService.fillPayload(target, dailyCast.getPayloadUrl(), dailyCast.getPayloadCacheKeys(), payloadCache);
            targets.add(f);
        }
        return payloadService.sequence(targets);
    }
}
