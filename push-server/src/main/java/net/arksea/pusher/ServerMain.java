package net.arksea.pusher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 * Created by xiaohaixing on 2017/1/30.
 */
public final class ServerMain {
    private static final Logger logger = LogManager.getLogger(ServerMain.class);
    private ServerMain() {};

    /**
     * @param args command line args
     */
    public static void main(final String[] args) {
        try {
            final ApplicationContext context = new ClassPathXmlApplicationContext(new String[] {"application-context.xml" });
            logger.info("启动推送服务{}",context.getApplicationName());
        } catch (BeansException ex) {
            LogManager.getLogger(ServerMain.class).error("启动推送服务失败", ex);
        }
    }
}
