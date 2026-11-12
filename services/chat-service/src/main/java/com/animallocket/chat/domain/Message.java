package com.animallocket.chat.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Index (conversationId, createdAt) phục vụ phân trang lịch sử theo thời gian.
 * Index (senderId, clientId) chống gửi trùng khi client thử lại; chỉ áp cho tin có clientId.
 */
@Document("messages")
@CompoundIndexes({
        @CompoundIndex(name = "conv_created", def = "{'conversationId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "sender_client", unique = true, def = "{'senderId': 1, 'clientId': 1}",
                partialFilter = "{'clientId': {$exists: true}}")
})
@Getter
@NoArgsConstructor
public class Message {
    public enum Type { TEXT, IMAGE, GLYPH }

    @Id
    private String id;
    private String conversationId;
    private String senderId;
    private Type type;
    /** Với GLYPH: fallback do server tạo từ nghĩa các glyph, để client cũ vẫn đọc được. */
    private String text;
    /** Ảnh trong chat cũng đi qua media pipeline: chỉ lưu mediaId của ảnh đã ẩn mặt. */
    private String mediaId;
    /** Chỉ có ở tin GLYPH, giữ đúng thứ tự người gửi chọn. */
    private List<String> glyphIds;
    /** Revision từ điển tại thời điểm gửi (chỉ tin GLYPH). */
    private Long dictionaryRevision;
    @Setter
    private String clientId;
    private Instant createdAt = Instant.now();

    public Message(String conversationId, String senderId, Type type, String text, String mediaId) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.type = type;
        this.text = text;
        this.mediaId = mediaId;
    }

    public static Message glyph(String conversationId, String senderId, List<String> glyphIds, String fallback,
                                long dictionaryRevision) {
        Message m = new Message(conversationId, senderId, Type.GLYPH, fallback, null);
        m.glyphIds = List.copyOf(glyphIds);
        m.dictionaryRevision = dictionaryRevision;
        return m;
    }
}
