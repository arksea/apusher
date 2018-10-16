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
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import javax.net.ssl.KeyManagerFactory;
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
    private List<ActorRef> pusherList;
    private List<ActorRef> pusherPoolList;
    private final int timeout = 100;
    private final int askAvailableTimeout = 100;
    private ActorRefFactory actorRefFactory;
    private Random random = new Random(System.currentTimeMillis());
    public ApnsPusher(String pusherName, int clientCount, String apnsTopic, ActorRefFactory actorRefFactory,
                      KeyManagerFactory keyManagerFactory, IPushStatusListener pushStatusListener) throws Exception {
        this.actorRefFactory = actorRefFactory;
        pusherList = new ArrayList<>(clientCount);
        pusherPoolList = new ArrayList<>(clientCount);
        for (int i=0;i<clientCount;++i) {
            String pushActorName = pusherName+"-"+i;
            Props props = ApnsPushActor.props(pushActorName,apnsTopic, keyManagerFactory,pushStatusListener);
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

    /**
     * @param event
     * @return 返回true表示推送提交成功，超时异常表示没有可用client，false表示推送失败
     */
    public Future<Boolean> push(PushEvent event) {
        Future<ActorRef> future = askAvailablePuseActor();
        return future.flatMap(
            mapper(pushActor ->
            Patterns.ask(pushActor, event, timeout).mapTo(classTag(Boolean.class)).map(
                mapper(ret -> ret),actorRefFactory.dispatcher()
            )
        ),actorRefFactory.dispatcher());
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
