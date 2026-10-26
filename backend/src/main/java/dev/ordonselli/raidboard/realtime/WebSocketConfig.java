package dev.ordonselli.raidboard.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import dev.ordonselli.raidboard.auth.SecurityProperties;

/**
 * STOMP over WebSocket.
 * <ul>
 *   <li>{@code /ws}: the endpoint browsers connect to;</li>
 *   <li>{@code /topic/events/{id}}: updates for everyone looking at an event;</li>
 *   <li>{@code /user/queue/notifications}: personal messages, delivered only to the sessions of one user.</li>
 * </ul>
 * The broker is Spring's in-memory one. It is enough for a single backend instance; with more instances
 * a message published on one would not reach clients connected to another, and a broker relay
 * (RabbitMQ, ActiveMQ) would be needed.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor stompAuthInterceptor;
    private final SecurityProperties securityProperties;

    public WebSocketConfig(StompAuthInterceptor stompAuthInterceptor, SecurityProperties securityProperties) {
        this.stompAuthInterceptor = stompAuthInterceptor;
        this.securityProperties = securityProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Same origin check as CORS: a page on another site can't open a socket in the user's name
        registry.addEndpoint("/ws")
                .setAllowedOrigins(securityProperties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }
}
