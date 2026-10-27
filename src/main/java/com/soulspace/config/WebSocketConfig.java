package com.soulspace.config;

import com.soulspace.service.ConsultService;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.security.Principal;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String CONSULT = "/topic/consult/";
    private static final String SEEKER = "/topic/seeker/";
    private static final String ROOM = "/topic/room/";

    private final ConsultService consults;
    private final com.soulspace.service.ChatAccessService access;

    public WebSocketConfig(ConsultService consults, com.soulspace.service.ChatAccessService access) {
        this.consults = consults;
        this.access = access;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Same-origin only (Spring's default Origin check); native WebSocket, no SockJS.
        registry.addEndpoint("/ws-soulspace");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(16 * 1024);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (acc != null && StompCommand.SUBSCRIBE.equals(acc.getCommand())) {
                    String dest = acc.getDestination();
                    String sid = acc.getSessionId();
                    if (dest != null) {
                        if (dest.startsWith(CONSULT)) {
                            Principal user = acc.getUser();
                            String code = dest.substring(CONSULT.length());
                            if (user == null || !consults.canAccess(code, user.getName())) {
                                throw new MessageDeliveryException("Forbidden");
                            }
                        } else if (dest.startsWith(SEEKER)) {
                            String clientId = dest.substring(SEEKER.length());
                            if (!access.canAccessSeeker(sid, clientId)) {
                                throw new MessageDeliveryException("Forbidden");
                            }
                        } else if (dest.startsWith(ROOM)) {
                            String roomId = dest.substring(ROOM.length());
                            if (!access.canAccessRoom(sid, roomId)) {
                                throw new MessageDeliveryException("Forbidden");
                            }
                        }
                    }
                }
                return message;
            }
        });
    }
}
