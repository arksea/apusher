package net.arksea.pusher.server.service;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.routing.ConsistentHashingRouter;
import net.arksea.pusher.entity.PushTarget;
import net.arksea.pusher.server.repository.PushTargetDao;
import net.arksea.acache.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import scala.concurrent.Future;

import java.util.List;

/**
 *
 * Created by xiaohaixing on 2018/3/5.
 */
@Component
public class PushTargetCacheFactory {
    private static Logger logger = LogManager.getLogger(PushTargetCacheFactory.class);
    @Autowired
    ActorSystem system;

    @Autowired
    PushTargetDao pushTargetDao;

    @Bean(name = "pushTargetCache")
    public CacheService<PushTargetKey, PushTarget> createCacheService() {
        PushTargetCacheSource source = new PushTargetCacheSource();
        Props props = CacheActor.propsOfCachePool(24, source);
        ActorRef ref = system.actorOf(props, "pushTargetCache");
        return new CacheService<PushTargetKey, PushTarget>(ref, system.dispatcher(), 5000);
    }

    class PushTargetCacheSource implements IDataSource<PushTargetKey, PushTarget> {
        @Override
        public Future<TimedData<PushTarget>> request(ActorRef actorRef, String cacheName, PushTargetKey key) {
            List<PushTarget> list = pushTargetDao.findByProductAndUserId(key.product, key.userId);
            logger.debug("query PushTarget from db: product={}, userId={}", key.product, key.userId);
            if (list.isEmpty()) {
                return Futures.successful(new TimedData<>(Long.MAX_VALUE, null));
            } else {
                return Futures.successful(new TimedData<>(Long.MAX_VALUE, list.get(0)));
            }
        }
        @Override
        public String getCacheName() {
            return "pushTargetCache";
        }
        public boolean waitForRespond() {
            return true;
        }
        public long getIdleTimeout(PushTargetKey key) {
            return 72000_000L; //20小时
        }
        public long getIdleCleanPeriod() {
            return 3600_000L;//60分钟
        }
    }

    public static class PushTargetKey implements ConsistentHashingRouter.ConsistentHashable {
        public final String product;
        public final String userId;
        PushTargetKey(String product, String uid) {
            this.product = product;
            this.userId = uid;
        }


        public int hashCode() {
            return product.hashCode()+userId.hashCode()*31;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PushTargetKey) {
                PushTargetKey other = (PushTargetKey) obj;
                return this.product.equals(other.product) && this.userId.equals(other.userId);
            } else {
                return false;
            }
        }

        @Override
        public Object consistentHashKey() {
            return userId;
        }
    }
}
