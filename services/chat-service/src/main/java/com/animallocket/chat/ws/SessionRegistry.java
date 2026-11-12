package com.animallocket.chat.ws;

import com.animallocket.common.kv.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Các phiên WebSocket đang mở TRÊN INSTANCE NÀY. Instance khác nhận tin qua MessageBus. */
public class SessionRegistry {
    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);
    private static final Duration CONN_TTL = Duration.ofSeconds(90);

    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final KeyValueStore kv;

    public SessionRegistry(KeyValueStore kv) {
        this.kv = kv;
    }

    public WebSocketSession add(String publicId, WebSocketSession raw) {
        // Decorator bảo vệ khi nhiều thread cùng ghi vào một phiên; ghi chậm quá 10 giây hoặc đầy bộ đệm thì ngắt.
        WebSocketSession s = new ConcurrentWebSocketSessionDecorator(raw, 10_000, 512 * 1024);
        sessions.computeIfAbsent(publicId, k -> ConcurrentHashMap.newKeySet()).add(s);
        touch(publicId);
        return s;
    }

    public void remove(String publicId, WebSocketSession s) {
        Set<WebSocketSession> set = sessions.get(publicId);
        if (set != null) {
            set.removeIf(x -> x.getId().equals(s.getId()));
            if (set.isEmpty()) {
                sessions.remove(publicId);
                kv.delete(connKey(publicId));
            }
        }
    }

    /** Đánh dấu người này đang có kết nối chat (để bỏ qua push khi họ đang mở app). */
    public void touch(String publicId) {
        kv.set(connKey(publicId), "1", CONN_TTL);
    }

    public boolean isConnectedAnywhere(String publicId) {
        return kv.get(connKey(publicId)).isPresent();
    }

    public void deliver(String publicId, String json) {
        Set<WebSocketSession> set = sessions.get(publicId);
        if (set == null) {
            return;
        }
        for (WebSocketSession s : set) {
            try {
                if (s.isOpen()) {
                    s.sendMessage(new TextMessage(json));
                }
            } catch (Exception e) {
                log.debug("Ghi vào phiên {} lỗi, đóng: {}", s.getId(), e.toString());
                try {
                    s.close(CloseStatus.SESSION_NOT_RELIABLE);
                } catch (Exception ignored) {
                    // đã đóng
                }
            }
        }
    }

    public int localSessionCount() {
        return sessions.values().stream().mapToInt(Set::size).sum();
    }

    private static String connKey(String id) {
        return "chat:conn:" + id;
    }
}
