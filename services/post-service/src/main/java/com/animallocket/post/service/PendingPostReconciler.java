package com.animallocket.post.service;

import com.animallocket.post.repo.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Lưới an toàn cho cuộc đua giữa "tạo bài" và PHOTO_READY.
 *
 * <p>Ảnh giờ READY gần như ngay khi client gọi {@code complete}, nên event thường tới đúng lúc client đang gọi
 * {@code POST /posts}. Hai transaction chạy song song, mỗi bên không thấy dòng chưa commit của bên kia: bài được
 * tạo ở PENDING_MEDIA, còn listener không tìm thấy bài để cập nhật. Không có event nào tới nữa nên bài kẹt mãi.
 * Bean này định kỳ tìm các bài như vậy và chuyển trạng thái chúng.
 */
@Component
public class PendingPostReconciler {
    private static final Logger log = LoggerFactory.getLogger(PendingPostReconciler.class);
    private static final int BATCH = 100;

    private final PostRepository posts;
    private final PostService service;

    public PendingPostReconciler(PostRepository posts, PostService service) {
        this.posts = posts;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${animal.post.reconcile-interval-ms:3000}",
            initialDelayString = "${animal.post.reconcile-interval-ms:3000}")
    public void reconcile() {
        List<String> ids = posts.findStuckPendingIds(PageRequest.of(0, BATCH));
        for (String id : ids) {
            try {
                service.settlePending(id);
            } catch (RuntimeException e) {
                log.warn("Không chuyển được trạng thái bài {}: {}", id, e.toString());
            }
        }
        if (!ids.isEmpty()) {
            log.info("Đã xử lý {} bài kẹt ở PENDING_MEDIA", ids.size());
        }
    }
}
