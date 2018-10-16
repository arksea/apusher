package net.arksea.pusher.sys;

import net.arksea.dsf.register.RegisterClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * Created by xiaohaixing on 2018/5/9.
 */
@Component
public class RegisterFactory {
    @Value("${dsf.enableRegisterService}")
    boolean enableRegisterService;

    @Value("${dsf.registerAddr1}")
    String dsfRegisterAddr1;

    @Value("${dsf.registerAddr2}")
    String dsfRegisterAddr2;

    @Value("${dsf.clientName}")
    String dsfClientName;

    @Bean
    RegisterClient create() {
        if (enableRegisterService) {
            List<String> addrs = new LinkedList<>();
            addrs.add(dsfRegisterAddr1);
            addrs.add(dsfRegisterAddr2);
            return new RegisterClient(dsfClientName, addrs);

        } else {
            return null;
        }
    }
}
