package com.animallocket.user.service;

import com.animallocket.common.web.ApiException;
import com.animallocket.user.domain.UserAvatar;
import com.animallocket.user.repo.AvatarPoolRepository;
import com.animallocket.user.repo.UserAvatarRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Random;

/**
 * Cấp một avatar CHƯA AI SỞ HỮU cho một người (docs/19-avatar-mechanism.md §4.2).
 * Chạy trong transaction của người gọi: transaction rollback thì việc giữ chỗ cũng rollback, avatar về trống.
 */
@Component
public class AvatarAllocator {
    static final int MAX_ATTEMPTS = 5;

    private final AvatarPoolRepository pool;
    private final UserAvatarRepository userAvatars;
    private final AvatarLibrary library;
    private final Random random;

    @org.springframework.beans.factory.annotation.Autowired
    public AvatarAllocator(AvatarPoolRepository pool, UserAvatarRepository userAvatars, AvatarLibrary library) {
        this(pool, userAvatars, library, new SecureRandom());
    }

    AvatarAllocator(AvatarPoolRepository pool, UserAvatarRepository userAvatars, AvatarLibrary library, Random random) {
        this.pool = pool;
        this.userAvatars = userAvatars;
        this.library = library;
        this.random = random;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AvatarLibrary.Avatar allocate(String publicId, UserAvatar.Source source) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            long free = pool.countByOwnerPublicIdIsNull();
            if (free == 0) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AVATAR_POOL_EXHAUSTED",
                        "Tạm thời hết avatar để cấp, vui lòng thử lại sau");
            }
            List<String> picked = pool.freeKeyAt(Math.floorMod(random.nextLong(), free));
            if (picked.isEmpty()) {
                continue; // kho vừa bị lấy bớt giữa lúc đếm và chọn
            }
            String key = picked.get(0);
            if (pool.claim(key, publicId, Instant.now()) == 1) {
                AvatarLibrary.Avatar avatar = library.find(key).orElseThrow(() ->
                        new IllegalStateException("Kho có khoá không thuộc thư viện: " + key));
                userAvatars.save(new UserAvatar(publicId, key, avatar.version(), source));
                return avatar;
            }
            // claim = 0: người khác vừa giữ đúng avatar này → chọn lại
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AVATAR_BUSY",
                "Hệ thống đang bận cấp avatar, vui lòng thử lại");
    }
}
