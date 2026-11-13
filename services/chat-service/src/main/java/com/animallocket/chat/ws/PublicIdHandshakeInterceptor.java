package com.animallocket.chat.ws;

import com.animallocket.common.Headers;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Xác thực ngay lúc bắt tay. JWT đã được Spring Security kiểm tra trước đó (HTTP Upgrade request), và gateway
 * đã gắn X-Public-Id. Không có publicId (chưa tạo hồ sơ) thì từ chối, không bao giờ mở phiên vô danh.
 */
public class PublicIdHandshakeInterceptor implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String id = request.getHeaders().getFirst(Headers.PUBLIC_ID);
        if (id == null || id.isBlank()) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(ChatWebSocketHandler.ATTR_PUBLIC_ID, id);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
        // không cần
    }
}
