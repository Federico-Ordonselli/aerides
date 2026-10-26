package dev.ordonselli.raidboard.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/** Replaces the SMTP sender auto-configured by Spring Boot (it backs off when this bean exists). */
@Configuration
@Profile("demo")
public class DemoMailConfig {

    @Bean
    public JavaMailSender javaMailSender() {
        return new LoggingMailSender();
    }
}
