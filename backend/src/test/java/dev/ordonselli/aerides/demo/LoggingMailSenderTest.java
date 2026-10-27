package dev.ordonselli.aerides.demo;

import static org.assertj.core.api.Assertions.assertThatCode;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.MimeMessageHelper;

class LoggingMailSenderTest {

    /** No SMTP host is configured: a real send would fail to connect, the demo sender only logs. */
    @Test
    void sendingWorksWithoutAnSmtpServer() throws Exception {
        LoggingMailSender sender = new LoggingMailSender();
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message);
        helper.setTo("player@example.com");
        helper.setSubject("Reminder");
        helper.setText("<p>Tomorrow</p>", true);

        assertThatCode(() -> sender.send(message)).doesNotThrowAnyException();
    }
}
