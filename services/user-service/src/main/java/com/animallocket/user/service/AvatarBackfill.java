package com.animallocket.user.service;

import com.animallocket.user.domain.UserAccount;
import com.animallocket.user.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Lúc khởi động (sau khi seed kho): cấp avatar ngẫu nhiên, duy nhất cho các tài khoản tạo trước khi có cơ chế avatar.
 * Giữ nguyên bạn bè/bài đăng/chat. Chạy lại chỉ xử lý người còn thiếu.
 */
@Component
@Order(20)
public class AvatarBackfill implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AvatarBackfill.class);

    private final UserRepository users;
    private final AvatarService avatars;
    private final boolean enabled;

    public AvatarBackfill(UserRepository users, AvatarService avatars,
                          @Value("${animal.avatar.backfill-on-startup:true}") boolean enabled) {
        this.users = users;
        this.avatars = avatars;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            runOnce();
        }
    }

    /** Trả số tài khoản vừa được cấp. */
    public int runOnce() {
        int assigned = 0;
        int failed = 0;
        for (String id : users.findPublicIdsWithoutAvatar(UserAccount.Status.DELETED)) {
            try {
                if (avatars.assignToExisting(id)) {
                    assigned++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Không cấp được avatar cho {}: {}", id, e.getMessage());
            }
        }
        if (assigned > 0 || failed > 0) {
            log.info("Cấp avatar cho tài khoản cũ: {} thành công, {} lỗi", assigned, failed);
        }
        return assigned;
    }
}
