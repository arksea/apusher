import akka.actor.*;
import java.util.*;
import groovy.json.JsonOutput;
import net.arksea.pusher.*
import akka.dispatch.OnComplete;
import net.arksea.dsf.register.RegisterClient
import java.time.*;
import com.fasterxml.jackson.databind.ObjectMapper;

class Test {
    ActorSystem sys
    ObjectMapper objectMapper = new ObjectMapper()

    public void runTest() {
        try {
            sys = akka.actor.ActorSystem.create("system")
            Thread.sleep(1000)
            List<String> addrs = new LinkedList<>();
            addrs.add("10.79.186.111:8777");
            addrs.add("10.79.186.126:8777");
            RegisterClient registerClient = new RegisterClient("pushClient test tool", addrs);
            PushService svc = new PushService(registerClient, "felink.huangli.ApnsPushServer-v1-online", 5000);
            getUserInfo(svc)
        } catch (Exception ex) {
            print ex
        }
        Thread.sleep ( 10000 )
        sys?.terminate ( )
        Thread.sleep ( 3000 )
    }

    def getCastJob(PushService svc) {
        svc.getCastJob(649).onComplete(
                new OnComplete<PushResult>() {
                    @Override
                    public void onComplete(Throwable ex, PushResult pushResult) {
                        if (ex != null) {
                            ex.printStackTrace()
                        } else if (pushResult.status != 0) {
                            println pushResult.message
                        } else {
                            println "==========================="
                            println pushResult.result.description
                            println "==========================="
                        }
                    }
                },sys.dispatcher()
        )
    }

    def getUserInfo(PushService svc) {
        svc.getUserInfo("115","33A324B6-DBA8-4701-8031-AEDB0883A46F").onComplete( //黄历天气
        //svc.getUserInfo("20000105","CF848FBD-8E8B-4515-99DF-6EC35C6BE5F0").onComplete( //黄历日历
                new OnComplete<PushResult>() {
                    @Override
                    public void onComplete(Throwable ex, PushResult pushResult) {
                        if (ex != null) {
                            ex.printStackTrace()
                        } else if (pushResult.status != 0) {
                            println pushResult.message
                        } else {
                            println "==========================="
                            println pushResult.result
                            println "==========================="
                        }
                    }
                },sys.dispatcher()
        )
    }

    public static void main(String[] args) {
        Test test = new Test()
        test.runTest()
    }
}