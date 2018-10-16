package net.arksea.pusher.server.repository;

import net.arksea.pusher.entity.UserDailyCast;
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
public interface UserDailyCastDao extends CrudRepository<UserDailyCast, Long> {
    @Query("select j from UserDailyCast j where j.enabled = true and (j.lastCreated is NULL or j.lastCreated < ?1)")
    List<UserDailyCast> getNotCreated(Timestamp date, Pageable pageable);

    @Modifying
    @Query("update UserDailyCast j set j.enabled = ?2 where j.id = ?1")
    void updateEnabled(long id, boolean enabled);

    int deleteByProductAndPayloadType(String product, String payloadType);
}
