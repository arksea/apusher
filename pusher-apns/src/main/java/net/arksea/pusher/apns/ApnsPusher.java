package net.arksea.pusher.apns;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import akka.pattern.Patterns;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.IPusher;
import net.arksea.pusher.PushEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import javax.net.ssl.KeyManagerFactory;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static akka.japi.Util.classTag;

/**
 * 使用多个ApnsPushActor进行推送
 * 先逐个用IsAvailable轮询，并用得到的可用client进行实际的推送
 * Created by xiaohaixing on 2017/12/11.
 */
public class ApnsPusher implements IPusher {
    private static final Logger logger = LogManager.getLogger(ApnsPusher.class);
    private final List<ActorRef> pusherList;
    private final int timeout = 1000;
    private final int askAvailableTimeout = 1000;
    private final ActorRefFactory actorRefFactory;
    private final Random random = new Random(System.currentTimeMillis());
    private final InetAddress[] apnsServerAddrArray;
    int pusherIndex;

    public ApnsPusher(String pusherName, int clientCount, String apnsTopic, ActorRefFactory actorRefFactory,
                      KeyManagerFactory keyManagerFactory, IPushStatusListener pushStatusListener) throws Exception {
        apnsServerAddrArray = InetAddress.getAllByName(ApnsClientUtils.APNS_HOST);
        logger.debug("APNS Server address count = " + apnsServerAddrArray.length);
        this.actorRefFactory = actorRefFactory;
        pusherList = new ArrayList<>(clientCount);
        for (int i=0;i<clientCount;++i) {
            String pushActorName = pusherName+"-"+i;
            String apnsAddr = getApnsAddress();
            Props props = ApnsPushActor.props(pushActorName, apnsAddr, apnsTopic, keyManagerFactory,pushStatusListener);
            ActorRef ref = actorRefFactory.actorOf(props);
            pusherList.add(ref);
        }
    }

    private String getApnsAddress() {
        return apnsServerAddrArray[(random.nextInt(apnsServerAddrArray.length - 1))].getHostAddress();
    }
    /**
     * @param event
     * @return 返回true表示推送提交成功，超时异常表示没有可用client，false表示推送失败
     */
    public Future<Boolean> push(PushEvent event) {
        ActorRef pusher;
        for (int n=0;n<pusherList.size();++n) {
            pusher = pusherList.get(pusherIndex);
            if (++pusherIndex >= pusherList.size()) {
                pusherIndex = 0;
            }
            if (pusherIsAvailable(pusher)) {
                return Patterns.ask(pusher, event, timeout).mapTo(classTag(Boolean.class));
            }
        }
        return Futures.successful(false);
    }

    private boolean pusherIsAvailable(ActorRef pusher) {
        try {
            final ApnsPushActor.AvailableReply request = new ApnsPushActor.AvailableReply();
            Future<Boolean> future = Patterns.ask(pusher, request, askAvailableTimeout).mapTo(classTag(Boolean.class));
            return Await.result(future, Duration.apply(askAvailableTimeout, TimeUnit.MICROSECONDS));
        } catch (Exception ex) {
            //logger.warn("ask PushActor available status failed: {}", pusher, ex);
            return false;
        }
    }
}
