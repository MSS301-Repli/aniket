package com.animallocket.post.messaging;

/** Event phát (qua outbox, topic post.events) khi tác giả xoá bài. */
public record PostDeleted(String postId, String authorPublicId) {
    public static final String TYPE = "POST_DELETED";
}
