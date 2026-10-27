package dev.ordonselli.aerides.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Closes a WebSocket session from the server side. Spring sends an ERROR frame with the reason to the
 * client and then closes the socket (StompSubProtocolHandler); closing a session removes all its
 * subscriptions from the broker. The client reads the reason to decide what to do next.
 */
@Component
public class StompSessionCloser {

    private static final Logger log = LoggerFactory.getLogger(StompSessionCloser.class);

    private final MessageChannel clientOutboundChannel;

    public StompSessionCloser(@Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel) {
        this.clientOutboundChannel = clientOutboundChannel;
    }

    public void close(String sessionId, String reason) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.ERROR);
        headers.setMessage(reason);
        headers.setSessionId(sessionId);
        try {
            clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
        } catch (MessagingException e) {
            log.warn("WebSocket session {} not closed", sessionId, e);
        }
    }
}
