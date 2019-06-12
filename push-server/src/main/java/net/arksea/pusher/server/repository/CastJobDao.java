package net.arksea.pusher.server.repository;

import net.arksea.pusher.CastType;
import net.arksea.pusher.entity.CastJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.util.List;

/**
 *
 * Created by xiaohaixing on 2017/7/5.
 */
public interface CastJobDao extends CrudRepository<CastJob, Long> {
    @Query("select j from CastJob j where j.startTime>=?1 order by j.startTime desc")
    List<CastJob> getFrom(Timestamp from, Pageable pageable);

    @Query("select j from CastJob j where j.startTime>=?2 and j.payloadType=?1 order by j.startTime desc")
    List<CastJob> getByPayloadtypeFrom(String payloadType, Timestamp from, Pageable pageable);

    @Query("select j from CastJob j where j.startTime between :from and :to and j.finishedTime is null and j.enabled = 1 and j.running = 0 and j.expiredTime>:to order by j.startTime")
    List<CastJob> getOnTimeBetween(@Param("from") Timestamp from, @Param("to") Timestamp to, Pageable pageable);

    @Modifying
    @Query("update CastJob j set j.running = 0")
    void resetRunningStatus();

    @Modifying
    @Query("delete CastJob j where j.startTime<?1")
    int deleteOldCastJob(Timestamp time);
}
