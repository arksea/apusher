package net.arksea.pusher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.support.AbstractApplicationContext;
import sun.misc.Signal;
import sun.misc.SignalHandler;

/**
 *
 * Created by xiaohaixing on 2018/11/22.
 */
public class TerminateHandler implements SignalHandler {
    private static final Logger logger = LogManager.getLogger(TerminateHandler.class);
    private final AbstractApplicationContext context;
    private Boolean terminated = false;
    private final Object monitor = new Object();

    public TerminateHandler(AbstractApplicationContext context) {
        this.context = context;
    }

    @Override
    public void handle(Signal signal) {
        logger.info("Terminate server by signal {}", signal.getName());
        terminateServer();
    }

    public void registerHook() {
        Signal.handle(new Signal("TERM"), this);
        Signal.handle(new Signal("USR2"), this);
    }

    public void waitForExist() {
        registerHook();
        synchronized (monitor) {
            while(!terminated) {
                try {
                    monitor.wait(60000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void terminateServer() {
        synchronized(monitor) {
            try {
                this.terminated = true;
                context.destroy();
            } catch (Exception ex) {
                logger.error("Destory context error", ex);
            }
            monitor.notifyAll();
        }
    }
}
