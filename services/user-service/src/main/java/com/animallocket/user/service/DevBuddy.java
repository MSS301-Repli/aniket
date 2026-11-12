package com.animallocket.user.service;

import com.animallocket.user.domain.UserAccount;
import com.animallocket.user.repo.BlockRepository;
import com.animallocket.user.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * CHỈ DÙNG KHI DEV: một tài khoản "bạn chung" (animal.dev-buddy.public-id) tự thành bạn của mọi người để test chat.
 * Tắt khi cấu hình trống. Tài khoản bạn chung là tài khoản đăng ký bình thường, nên đăng nhập được để trả lời.
 */
@Component
public class DevBuddy {
    private static final Logger log = LoggerFactory.getLogger(DevBuddy.class);

    private final String buddyId;
    private final UserRepository users;
    private final BlockRepository blocks;
    private final FriendService friends;

    public DevBuddy(@Value("${animal.dev-buddy.public-id:}") String buddyId, UserRepository users,
                    BlockRepository blocks, FriendService friends) {
        this.buddyId = buddyId == null ? "" : buddyId.trim();
        this.users = users;
        this.blocks = blocks;
        this.friends = friends;
    }

    public boolean enabled() {
        return !buddyId.isEmpty();
    }

    /** Gọi trong transaction đăng ký: kết bạn tài khoản mới với bạn chung (nếu bật và bạn chung còn hoạt động). */
    public void befriend(String publicId) {
        if (!enabled() || buddyId.equals(publicId) || !buddyActive()) {
            return;
        }
        if (!blocks.existsEitherWay(buddyId, publicId)) {
            friends.forceFriends(buddyId, publicId);
        }
    }

    /** Bù cho các tài khoản tạo trước khi bật. Người đã huỷ kết bạn sẽ được kết bạn lại; người đã chặn thì không. */
    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        if (!enabled()) {
            return;
        }
        try {
            if (!buddyActive()) {
                log.warn("animal.dev-buddy.public-id={} không phải tài khoản đang hoạt động; bỏ qua", buddyId);
                return;
            }
            int n = 0;
            for (UserAccount u : users.findAll()) {
                if (u.isActive() && !buddyId.equals(u.getPublicId()) && !friends.areFriends(buddyId, u.getPublicId())
                        && !blocks.existsEitherWay(buddyId, u.getPublicId())) {
                    friends.forceFriends(buddyId, u.getPublicId());
                    n++;
                }
            }
            log.warn("DEV BUDDY đang bật ({}): đã kết bạn thêm {} tài khoản. Không bật trên môi trường thật.", buddyId, n);
        } catch (RuntimeException e) {
            log.error("Không bù được bạn chung", e);
        }
    }

    private boolean buddyActive() {
        return users.findByPublicId(buddyId).filter(UserAccount::isActive).isPresent();
    }
}
