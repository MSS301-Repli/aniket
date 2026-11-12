package com.animallocket.user.service;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Topics;
import com.animallocket.common.event.Events;
import com.animallocket.outbox.OutboxService;
import com.animallocket.user.domain.UserAvatar;
import com.animallocket.user.dto.Dtos;
import com.animallocket.user.repo.UserAvatarRepository;
import com.animallocket.user.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AvatarService {
    private final UserRepository users;
    private final UserAvatarRepository userAvatars;
    private final AvatarAllocator allocator;
    private final OutboxService outbox;

    public AvatarService(UserRepository users, UserAvatarRepository userAvatars, AvatarAllocator allocator,
                         OutboxService outbox) {
        this.users = users;
        this.userAvatars = userAvatars;
        this.allocator = allocator;
        this.outbox = outbox;
    }

    /**
     * Cấp avatar ngẫu nhiên cho tài khoản cũ chưa có. Idempotent: người đã có avatar thì không làm gì (false).
     * Mỗi người một transaction để một lỗi không làm hỏng cả đợt.
     */
    @Transactional
    public boolean assignToExisting(String publicId) {
        if (users.findByPublicId(publicId).map(u -> u.getAvatarKey() != null).orElse(true)) {
            return false;
        }
        AvatarLibrary.Avatar a = allocator.allocate(publicId, UserAvatar.Source.BACKFILL);
        if (users.assignAvatarIfMissing(publicId, a.key(), a.version(), a.animal()) != 1) {
            // Instance khác vừa gán xong: huỷ cả transaction để avatar vừa giữ trở về kho.
            throw new IllegalStateException("Avatar của " + publicId + " vừa được gán ở nơi khác");
        }
        outbox.publish(Topics.USER, EventTypes.AVATAR_CHANGED, publicId,
                new Events.AvatarChanged(publicId, a.code(), a.animal()));
        outbox.publish(Topics.USER, EventTypes.LEAD_ANIMAL_CHANGED, publicId,
                new Events.LeadAnimalChanged(publicId, a.animal()));
        return true;
    }

    @Transactional(readOnly = true)
    public List<Dtos.AvatarView> collection(String publicId) {
        return userAvatars.findByPublicIdOrderByCreatedAtAsc(publicId).stream()
                .map(v -> new Dtos.AvatarView(v.getLibVersion() + ":" + v.getAvatarKey(), v.getSource().name(),
                        v.getCreatedAt()))
                .toList();
    }
}
