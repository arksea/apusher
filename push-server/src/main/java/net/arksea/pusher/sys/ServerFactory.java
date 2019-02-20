package net.arksea.pusher.sys;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import net.arksea.dsf.codes.ICodes;
import net.arksea.dsf.codes.JavaSerializeCodes;
import net.arksea.dsf.register.RegisterClient;
import net.arksea.pusher.server.PushServer;
import net.arksea.pusher.server.PushServerStat;
import net.arksea.pusher.server.service.PushTargetManager;
import net.arksea.pusher.server.service.PushTargetManagerState;
import net.arksea.pusher.server.cast.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 *
 * Created by xiaohaixing on 2017/1/31.
 */
@Component
public class ServerFactory {
    @Autowired
    ActorSystem system;

    @Autowired
    PushServerStat pushServerStat;

    @Autowired
    JobResources jobResources;
    @Autowired
    CastJobManagerState castJobManagerState;

    @Value("${push.dailyCast.cleanJobDays}")
    int dailyCastCleanJobDays;

    @Value("${push.pushTarget.autoClean}")
    boolean pushTargetAutoClean;

    @Value("${push.userDailyCast.enabled}")
    boolean userDailyCastEnabled;

    @Resource(name="serverProfile")
    String serverProfile;

    @Value("${dsf.serviceRegisterName}")
    String serviceRegisterName;

    @Autowired
    RegisterClient registerClient;

    @Resource(name="systemConfig")
    Config systemConfig;

    @Autowired
    PushTargetManagerState pushTargetManagerState;

    @Bean(name = "castJobManager")
    public ActorRef createCastJobManager() {
        return system.actorOf(CastJobManager.props(castJobManagerState),"castJobManager");
    }

    @Bean(name = "userDailyCastCreater")
    public ActorRef createUserDailyCastJobManager() {
        if (userDailyCastEnabled) {
            return system.actorOf(UserDailyCastJobCreater.props(jobResources, dailyCastCleanJobDays), "userDailyCastCreater");
        } else {
            return null;
        }
    }

    @Bean(name = "dailyCastCreater")
    public ActorRef createDailyCastJobManager() {
        return system.actorOf(DailyCastJobCreater.props(jobResources, dailyCastCleanJobDays),"dailyCastCreater");
    }

    @Bean(name = "pushTargetManager")
    public ActorRef createPushTargetManager() {
        if (pushTargetAutoClean) {
            return system.actorOf(PushTargetManager.props(pushTargetManagerState), "pushTargetManager");
        } else {
            return null;
        }
    }

    @Bean(name = "arkseaPushServer")
    public ActorRef createPushServer() throws UnknownHostException {
        ActorRef actorRef = system.actorOf(PushServer.props(pushServerStat),"arkseaPushServer");
        if (registerClient != null) {
            int bindPort = systemConfig.getInt("akka.remote.netty.tcp.port");
            String regname = serviceRegisterName + "-v1-" + serverProfile;
            String hostAddrss = InetAddress.getLocalHost().getHostAddress();
            ICodes codes = new JavaSerializeCodes();
            registerClient.register(regname, hostAddrss, bindPort, actorRef, system, codes);
        }
        return actorRef;
    }
}
