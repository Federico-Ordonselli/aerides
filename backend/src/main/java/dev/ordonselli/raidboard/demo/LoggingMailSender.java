package dev.ordonselli.raidboard.demo;

import java.util.Arrays;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Demo deployment only: writes a log line instead of sending the email. The free hosting blocks SMTP
 * ports, and a public demo shouldn't send emails to addresses people type in. Everything before the
 * send (templates, the notifications table, exactly-once reminders) runs as usual.
 */
public class LoggingMailSender extends JavaMailSenderImpl {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
        for (MimeMessage message : mimeMessages) {
            try {
                log.info("Email not sent (demo profile): to={} subject=\"{}\"",
                        Arrays.toString(message.getAllRecipients()), message.getSubject());
            } catch (MessagingException e) {
                throw new MailPreparationException(e);
            }
        }
    }
}
