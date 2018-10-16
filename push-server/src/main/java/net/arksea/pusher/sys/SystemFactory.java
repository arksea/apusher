package net.arksea.pusher.sys;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.arksea.dsf.register.RegisterClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2017/1/31.
 */
@Component
public class SystemFactory {
    private final Logger logger = LogManager.getLogger(SystemFactory.class);

    @Autowired
    RegisterClient registerClient;

    @Autowired
    private Environment env;

    Config config;
    ActorSystem system;

    public SystemFactory() {
        Config cfg = ConfigFactory.load();
        config = cfg.getConfig("system").withFallback(cfg);
    }

    @Bean(name = "systemConfig")
    Config createConfig() {
        return config;
    }

    @Bean(name = "system")
    public ActorSystem createSystem() {
        system = ActorSystem.create("system", config);
        return system;
    }

    @Bean(name = "serverProfile")
    public String createServerProfile() {
        String active = this.env.getProperty("spring.profiles.active");
        if (active == null) {
            return "online";
        }
        switch (active) {
            case "functional-test":
            case "unit-test":
                return "QA";
            case "development":
                return "DEV";
            default:
                return "online";
        }
    }

    @PreDestroy
    public void stop() {
        try {
            logger.info("Stopping config server system");
            Future f = system.terminate();
            Await.result(f, Duration.apply(10, TimeUnit.SECONDS));
            logger.info("Config server system stopped");
        } catch (Exception e) {
            logger.warn("Stop config server system timeout", e);
        }
        if (registerClient != null) {
            registerClient.stopAndWait(10);
        }
    }
}
