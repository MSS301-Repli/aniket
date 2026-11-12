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
 * Ký hiệu (glyph) dùng chung trong một hội thoại. Đã xuất bản thì bất biến: chỉ có thể archive (xoá mềm),
 * muốn đổi hình hoặc nghĩa phải tạo glyph mới.
 *
 * <p>{@code revision} tăng dần trong từng hội thoại, mỗi lần tạo hoặc archive gán giá trị mới = max + 1.
 * Index unique (conversationId, revision) bảo đảm không trùng; người ghi nào va chạm thì thử lại. Nhờ vậy
 * revision N+1 chỉ được ghi sau khi N đã thấy được, nên delta sync theo {@code afterRevision} không bỏ sót.
 */
@Document("conversation_glyphs")
@CompoundIndexes({
        @CompoundIndex(name = "conv_creator_mutation", unique = true,
                def = "{'conversationId': 1, 'creatorPublicId': 1, 'clientMutationId': 1}"),
        @CompoundIndex(name = "conv_revision", unique = true, def = "{'conversationId': 1, 'revision': 1}"),
        @CompoundIndex(name = "conv_status", def = "{'conversationId': 1, 'status': 1}"),
        // Hình canonical là duy nhất trong các glyph còn ACTIVE; glyph đã archive không chặn tạo lại.
        @CompoundIndex(name = "conv_shape_active", unique = true, def = "{'conversationId': 1, 'shapeHash': 1}",
                partialFilter = "{'status': 'ACTIVE'}")
})
@Getter
@Setter
@NoArgsConstructor
public class ConversationGlyph {
    public enum Status { ACTIVE, ARCHIVED }

    @Id
    private String id;
    private String conversationId;
    private String creatorPublicId;
    private String clientMutationId;
    private String meaning;
    private String color;
    /** 64 cặp (x, y) đã canonical hoá, 128 số nguyên trong miền -1000..1000. */
    private List<Integer> normalizedPoints;
    private String shapeHash;
    private Status status = Status.ACTIVE;
    private long revision;
    private Instant createdAt = Instant.now();
    private Instant archivedAt;
}
