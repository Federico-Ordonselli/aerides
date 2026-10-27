package dev.ordonselli.aerides.support;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * An in-memory SMTP server (GreenMail) that lives as long as the Spring context, on a free port.
 * The application sends real emails to it, and tests read them back.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestMailConfiguration {

    @Bean(initMethod = "start", destroyMethod = "stop")
    GreenMail greenMail() {
        return new GreenMail(ServerSetupTest.SMTP.dynamicPort());
    }

    @Bean
    DynamicPropertyRegistrar mailServerProperties(GreenMail greenMail) {
        return registry -> {
            registry.add("spring.mail.host", () -> greenMail.getSmtp().getBindTo());
            registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
        };
    }
}
