package net.arksea.pusher.server.repository;

import net.arksea.pusher.entity.UserDailyTimer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 *
 * Created by xiaohaixing on 2018/2/5.
 */
public interface UserDailyTimerDao extends CrudRepository<UserDailyTimer, Long> {

    @Query("select c from UserDailyTimer c where c.userId = :u and c.product = :p and c.payloadType = :t and c.timerId = :tid")
    List<UserDailyTimer> findByKey(@Param("u") final String userId,
                                   @Param("p") final String product,
                                   @Param("t") final String payloadType,
                                   @Param("tid") final long timerId);

    @Query("select c from UserDailyTimer c where c.userId = :u and c.product = :p")
    List<UserDailyTimer> findByUserIdAndProduct(@Param("u") final String userId,
                                                @Param("p") final String product);

    @Query("select c from UserDailyTimer c where product = ?1 and minuteOfDay = ?2 and c.payloadType = ?3")
    List<UserDailyTimer> findByMinuteOfDay(String product, int minuteOfDay, String payloadType, Pageable pageable);

    @Query("select count(1) from UserDailyTimer c where c.product = ?1 and c.minuteOfDay = ?2 and c.payloadType = ?3")
    long countByMinuteOfDayAndPayloadType(String product, int minuteOfDay, String payloadType);

    @Query("update UserDailyTimer c set c.enabled = false, c.deleted = true where c.product = ?1 and c.userId = ?2")
    @Modifying
    int setTimerDeleted(String product, String userId);

    @Query("update UserDailyTimer c set c.enabled = :e where c.userId = :u and c.product = :p and c.payloadType = :t and c.timerId = :tid")
    @Modifying
    int updateTimerStatus(@Param("u") final String userId,
                          @Param("p") final String product,
                          @Param("t") final String payloadType,
                          @Param("tid") final long timerId,
                          @Param("e") final boolean enabled);

    @Query("update UserDailyTimer c set c.enabled = false, c.deleted = true where c.userId = :u and c.product = :p and c.payloadType = :t and c.timerId = :tid")
    @Modifying
    int setTimerDeleted(@Param("u") final String userId,
                        @Param("p") final String product,
                        @Param("t") final String payloadType,
                        @Param("tid") final long timerId);

    @Query("select p from UserDailyTimer p where p.partitions = :n and p.product = :p and p.minuteOfDay = :m and p.payloadType = :t and p.enabled = true and p.userId > :u order by p.userId")
    List<UserDailyTimer> findByPartition(@Param("n") int partition,
                                         @Param("p") String product,
                                         @Param("m") int minuteOfDay,
                                         @Param("t") String payloadType,
                                         @Param("u") String fromUserId, Pageable pageable);

    @Query("select p from UserDailyTimer p where p.partitions = :n and product = :p and p.minuteOfDay = :m and p.payloadType = :t and p.enabled = true order by p.userId")
    List<UserDailyTimer> findByPartition(@Param("n") int partition,
                                         @Param("p") String product,
                                         @Param("m") int minuteOfDay,
                                         @Param("t") String payloadType, Pageable pageable);

    @Query("delete UserDailyTimer where deleted = true")
    @Modifying
    int cleanDeleted();

    @Query("delete UserDailyTimer where partitions = ?1 and deleted = true")
    @Modifying
    int cleanDeletedByPartition(int partition);
}
