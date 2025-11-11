package com.kt.social.infra.websocket;

import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// 🔥🔥 IMPORT QUAN TRỌNG NHẤT 🔥🔥
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
@Component
public class StompPrincipalInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (accessor.getUser() == null) {
            if (accessor.getSessionAttributes() != null) {
                Object userId = accessor.getSessionAttributes().get("userId");
                if (userId != null) {
                    String principalName = userId.toString();
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            principalName,
                            null,
                            Collections.emptyList()
                    );
                    accessor.setUser(auth);
                    if (SecurityContextHolder.getContext().getAuthentication() == null) {
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } else {
                    log.warn("STOMP Interceptor: ❌ Không tìm thấy 'userId' trong session!");
                }
            } else {
                log.error("STOMP Interceptor: ❌ Lỗi nghiêm trọng! Session attributes là null!");
            }
        }

        return message;
    }

    @Override
    public void postSend(@NonNull Message<?> message, @NonNull MessageChannel channel, boolean sent) {
        SecurityContextHolder.clearContext();
    }
}