package net.arksea.pusher.server.repository;

import net.arksea.pusher.entity.PushTarget;
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
public interface PushTargetDao extends CrudRepository<PushTarget, Long> {

    List<PushTarget> findByProductAndUserId(final String product, final String userId);

    List<PushTarget> findByProductAndToken(final String product, final String token);

    @Query("update PushTarget c set c.tokenActived = :a where c.token = :t")
    @Modifying
    int updateTokenStatus(@Param("t") final String token,@Param("a") final Boolean tokenActived);

    @Query("update PushTarget c set c.tokenActived = :a where c.userId = :uid")
    @Modifying
    int updateTokenStatusByUserId(@Param("uid") final String userId,@Param("a") final Boolean tokenActived);

    @Query("select p from PushTarget p where p.partitions = ?1 and p.token != '' and product = ?2 and tokenActived = true and p.userId > ?3 order by p.userId")
    List<PushTarget> findByPartitionAndProduct(int partition, String product, String fromUserId, Pageable pageable);

    @Query("select p from PushTarget p where p.partitions = ?1 and product = ?2 and tokenActived = true and p.token != '' order by p.userId")
    List<PushTarget> findByPartitionAndProduct(int partition, String product, Pageable pageable);

    @Query("select p from PushTarget p where product = ?1 and tokenActived = true and p.token != '' and p.situs = ?2 order by p.userId")
    List<PushTarget> findByProductAndSitus(String product, String situs, Pageable pageable);

    @Query("select p from PushTarget p where product = ?1 and tokenActived = true and p.token != '' and p.userId > ?2 and p.situs = ?3 order by p.userId")
    List<PushTarget> findByProductAndSitus(String product, String fromUserId, String situs, Pageable pageable);

    @Query("select p from PushTarget p where p.partitions = ?1 and product = ?2 and tokenActived = true and p.token != '' and p.situsGroup in (?3) order by p.userId")
    List<PushTarget> findByPartitionAndProductAndSitusGroup(int partition, String product, List<String> situsGroups, Pageable pageable);

    @Query("select p from PushTarget p where p.partitions = ?1 and product = ?2 and tokenActived = true and p.token != '' and p.userId > ?3 and p.situsGroup in (?4) order by p.userId")
    List<PushTarget> findByPartitionAndProductAndSitusGroup(int partition, String product, String fromUserId, List<String> situsGroups, Pageable pageable);

    @Query("select p from PushTarget p where p.partitions = ?1 and p.lastUpdate < ?2")
    List<PushTarget> findLastUpdateBefore(int partition, Timestamp before, Pageable pageable);

    @Query("delete PushTarget p where p.lastUpdate < ?1")
    @Modifying
    int deleteBefore(Timestamp before);

    @Query("delete PushTarget p where p.partitions = ?1 and p.lastUpdate < ?2")
    @Modifying
    int deleteBeforeByPartiton(int partitions, Timestamp before);

    @Query("update PushTarget c set c.userInfo = ?3 where c.product = ?1 and c.userId = ?2")
    @Modifying
    int updateUserInfo(String product, String userId, String userInfo);

    //@Query("select p.userInfo from PushTarget p where c.product = ?1 and c.userId = ?2")
    List<String> getUserInfoByProductAndUserId(String product, String userId);

    @Query("select count(1) from PushTarget c where c.partitions = ?1 and c.product = ?2")
    long countByPartitionAndProduct(int partitions, String product);

    @Query("select count(1) from PushTarget c where c.situs = ?1 and c.product = ?2")
    long countBySitusAndProduct(String situs, String product);
}
