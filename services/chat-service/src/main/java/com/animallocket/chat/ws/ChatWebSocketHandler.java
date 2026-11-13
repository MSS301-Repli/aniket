package com.animallocket.chat.ws;

import com.animallocket.chat.domain.Message;
import com.animallocket.chat.service.ChatService;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Giao thức (JSON, mỗi khung có "type"):
 * <pre>
 *  client → server:  {"type":"send","conversationId":"..","text":"..","clientId":".."}
 *                    {"type":"send","conversationId":"..","messageType":"IMAGE","mediaId":"..","clientId":".."}
 *                    {"type":"send","conversationId":"..","messageType":"GLYPH","glyphIds":[..],"clientId":".."}
 *                    {"type":"read","conversationId":".."}   {"type":"ping"}
 *  server → client:  {"type":"message","data":{..}}  {"type":"ack","data":{"clientId":..,"messageId":..}}
 *                    {"type":"dictionary_changed","data":{"conversationId":..,"revision":..,"glyphId":..,"change":"UPSERT|ARCHIVE"}}
 *                    {"type":"read","data":{..}}  {"type":"closed","data":{..}}  {"type":"error","data":{"code":..}}  {"type":"pong"}
 * </pre>
 */
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    static final String ATTR_PUBLIC_ID = "publicId";
    static final String ATTR_SESSION = "decorated";
    private static final int MAX_FRAME = 16 * 1024;

    private final SessionRegistry registry;
    private final ChatService chat;

    public ChatWebSocketHandler(SessionRegistry registry, ChatService chat) {
        this.registry = registry;
        this.chat = chat;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String me = (String) session.getAttributes().get(ATTR_PUBLIC_ID);
        session.getAttributes().put(ATTR_SESSION, registry.add(me, session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String me = (String) session.getAttributes().get(ATTR_PUBLIC_ID);
        if (message.getPayloadLength() > MAX_FRAME) {
            session.close(CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }
        JsonNode in;
        try {
            in = EventJson.mapper().readTree(message.getPayload());
        } catch (Exception e) {
            reply(session, "error", Map.of("code", "BAD_FRAME"));
            return;
        }
        String type = in.path("type").asText("");
        String clientId = in.path("clientId").asText(null);
        try {
            switch (type) {
                case "ping" -> {
                    registry.touch(me);
                    reply(session, "pong", Map.of());
                }
                case "read" -> chat.markRead(me, in.path("conversationId").asText());
                case "send" -> {
                    Message.Type t = messageType(in.path("messageType").asText("TEXT"));
                    var sent = chat.send(me, in.path("conversationId").asText(),
                            new ChatService.SendCommand(t, textOrNull(in, "text"), textOrNull(in, "mediaId"),
                                    glyphIds(in), clientId));
                    reply(session, "ack", Map.of("clientId", clientId == null ? "" : clientId, "messageId", sent.id()));
                }
                default -> reply(session, "error", Map.of("code", "UNKNOWN_TYPE"));
            }
        } catch (ApiException e) {
            reply(session, "error", Map.of("code", e.code(), "message", e.getMessage(),
                    "clientId", clientId == null ? "" : clientId));
        } catch (RuntimeException e) {
            log.error("Lỗi xử lý khung WebSocket", e);
            reply(session, "error", Map.of("code", "INTERNAL_ERROR"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object me = session.getAttributes().get(ATTR_PUBLIC_ID);
        if (me != null) {
            registry.remove((String) me, session);
        }
    }

    /** Client cũ không gửi messageType (hoặc gửi giá trị lạ) vẫn được hiểu là TEXT như trước. */
    private static Message.Type messageType(String raw) {
        return switch (raw) {
            case "IMAGE" -> Message.Type.IMAGE;
            case "GLYPH" -> Message.Type.GLYPH;
            default -> Message.Type.TEXT;
        };
    }

    private static String textOrNull(JsonNode in, String field) {
        JsonNode n = in.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    /** null nếu thiếu hoặc không phải mảng chuỗi; ChatService sẽ trả INVALID_GLYPH_MESSAGE. */
    private static List<String> glyphIds(JsonNode in) {
        JsonNode arr = in.get("glyphIds");
        if (arr == null || !arr.isArray()) {
            return null;
        }
        List<String> ids = new ArrayList<>(arr.size());
        for (JsonNode n : arr) {
            ids.add(n.isTextual() ? n.asText() : null);
        }
        return ids;
    }

    private void reply(WebSocketSession session, String type, Object data) throws Exception {
        WebSocketSession target = (WebSocketSession) session.getAttributes().getOrDefault(ATTR_SESSION, session);
        target.sendMessage(new TextMessage(ChatService.frame(type, data)));
    }
}
