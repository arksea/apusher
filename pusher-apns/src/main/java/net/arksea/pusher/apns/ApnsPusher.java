package net.arksea.pusher.apns;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.Props;
import akka.dispatch.Mapper;
import akka.pattern.Patterns;
import akka.routing.ScatterGatherFirstCompletedGroup;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.IPusher;
import net.arksea.pusher.PushEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import javax.net.ssl.KeyManagerFactory;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static akka.japi.Util.classTag;

/**
 * 使用多个ApnsPushActor进行推送
 * 先逐个用IsAvailable轮询，并用得到的可用client进行实际的推送
 * Created by xiaohaixing on 2017/12/11.
 */
public class ApnsPusher implements IPusher {
    private static final Logger logger = LogManager.getLogger(ApnsPusher.class);
    private final List<ActorRef> pusherList;
    private final List<ActorRef> pusherPoolList;
    private final int timeout = 100;
    private final int askAvailableTimeout = 100;
    private final ActorRefFactory actorRefFactory;
    private final Random random = new Random(System.currentTimeMillis());
    private final InetAddress[] apnsServerAddrArray;

    public ApnsPusher(String pusherName, int clientCount, String apnsTopic, ActorRefFactory actorRefFactory,
                      KeyManagerFactory keyManagerFactory, IPushStatusListener pushStatusListener) throws Exception {
        apnsServerAddrArray = InetAddress.getAllByName(ApnsClientUtils.APNS_HOST);
        logger.debug("APNS Server address count = " + apnsServerAddrArray.length);
        this.actorRefFactory = actorRefFactory;
        pusherList = new ArrayList<>(clientCount);
        pusherPoolList = new ArrayList<>(clientCount);
        for (int i=0;i<clientCount;++i) {
            String pushActorName = pusherName+"-"+i;
            String apnsAddr = getApnsAddress();
            Props props = ApnsPushActor.props(pushActorName, apnsAddr, apnsTopic, keyManagerFactory,pushStatusListener);
            ActorRef ref = actorRefFactory.actorOf(props);
            pusherList.add(ref);
        }
        for (int i=0;i<clientCount;++i) {
            List<String> paths = new LinkedList<>();
            Set<Integer> indexSet = new LinkedHashSet<>();
            indexSet.add(i);
            paths.add(pusherList.get(i).path().toStringWithoutAddress());
            for (int m=0;m<10;++m) {
                int index = random.nextInt(pusherList.size());
                if (!indexSet.contains(index)) {
                    indexSet.add(index);
                    paths.add(pusherList.get(index).path().toStringWithoutAddress());
                    if (indexSet.size() >= 3) {
                        break;
                    }
                }
            }
            Props props = new ScatterGatherFirstCompletedGroup(paths, Duration.create(askAvailableTimeout, TimeUnit.MILLISECONDS)).props();
            ActorRef pool = actorRefFactory.actorOf(props);
            pusherPoolList.add(pool);
        }
    }

    private String getApnsAddress() {
        return apnsServerAddrArray[(random.nextInt(apnsServerAddrArray.length - 1))].getHostAddress();
    }
    /**
     * 提交成功就返回结果，而非等收到APNS推送回执结果才返回，是为了提高推送吞吐率，减少快慢设备间的阻塞。
     * APNS推送回执结果将通过回调接口pushStatusListener异步通知CastJobActor，CastJobActor收到回执
     * 结果会进行相应的计数处理，同时移除submitedEvents中的event，当任务结束时，submitedEvent还有
     * 内容就表示这些推送没有收到明确的成功或失败应答（通常是网络连接中断造成）
     * @param event
     * @return 返回true表示提交给PushActor成功，false表示PushActor不可用， 超时异常表示没有可用PushActor
     */
    public Future<Boolean> push(PushEvent event) {
        Future<ActorRef> future = askAvailablePuseActor();
        return future.flatMap(
            mapper(pushActor ->
                Patterns.ask(pushActor, event, timeout).mapTo(classTag(Boolean.class))
            ),actorRefFactory.dispatcher()
        );
    }
    public static <T,R> Mapper<T,R> mapper(Function<T,R> func) {
        return new Mapper<T, R>() {
            @Override
            public R apply(T t) {
                return func.apply(t);
            }
        };
    }
    /**
     * 获取第一个可用状态的pushActor
     * @return
     */
    private Future<ActorRef> askAvailablePuseActor() {
        final ApnsPushActor.AvailableReply request = new ApnsPushActor.AvailableReply();
        int index = random.nextInt(pusherPoolList.size());
        ActorRef pool = pusherPoolList.get(index);
        return Patterns.ask(pool, request, askAvailableTimeout).mapTo(classTag(ActorRef.class));
    }
}
