package dev.ordonselli.aerides.notification;

import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Sends HTML emails through the SMTP server configured in {@code spring.mail.*} (Mailpit in development). */
@Component
public class EmailSender {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public EmailSender(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void send(EmailMessage email) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.mailFrom());
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.html(), true);
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
        mailSender.send(message);
    }
}
