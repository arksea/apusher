package net.arksea.pusher.server.repository;

import net.arksea.pusher.entity.DailyCast;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.sql.Timestamp;
import java.util.List;

/**
 *
 * Created by xiaohaixing on 2017/7/5.
 */
public interface DailyCastDao extends CrudRepository<DailyCast, Long> {
    @Query("select j from DailyCast j where j.enabled = true and (j.lastCreated is NULL or j.lastCreated < ?1)")
    List<DailyCast> getNotCreated(Timestamp date, Pageable pageable);

    @Modifying
    @Query("update DailyCast j set j.enabled = ?2 where j.id = ?1")
    void updateEnabled(long id, boolean enabled);

    int deleteByProductAndPayloadType(String product, String payloadType);
}
