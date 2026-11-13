package com.animallocket.chat.web;

import com.animallocket.chat.domain.Message;
import com.animallocket.chat.service.ChatService;
import com.animallocket.common.Headers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** REST dự phòng cho WebSocket (tải lịch sử, danh sách hội thoại, gửi khi mất kết nối realtime). */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    public record OpenRequest(@NotBlank String withPublicId) {
    }

    /** GLYPH: chỉ gửi {@code glyphIds} (+ clientId), không gửi text; fallback do server tạo. */
    public record SendRequest(Message.Type type, @Size(max = ChatService.MAX_TEXT) String text, String mediaId,
                              List<String> glyphIds, String clientId) {
    }

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping("/conversations")
    public ChatService.ConversationView open(@RequestHeader(Headers.PUBLIC_ID) String me,
                                             @Valid @RequestBody OpenRequest req) {
        return chat.open(me, req.withPublicId());
    }

    @GetMapping("/conversations")
    public List<ChatService.ConversationView> mine(@RequestHeader(Headers.PUBLIC_ID) String me) {
        return chat.mine(me);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<ChatService.MessageView> history(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String id,
                                                 @RequestParam(required = false) Instant before,
                                                 @RequestParam(defaultValue = "30") int limit) {
        return chat.history(me, id, before, limit);
    }

    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatService.MessageView send(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String id,
                                        @Valid @RequestBody SendRequest req) {
        return chat.send(me, id, new ChatService.SendCommand(req.type(), req.text(), req.mediaId(),
                req.glyphIds(), req.clientId()));
    }

    @PostMapping("/conversations/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String id) {
        chat.markRead(me, id);
    }
}
