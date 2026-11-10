package com.animallocket.user.service;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Topics;
import com.animallocket.common.event.Events;
import com.animallocket.common.web.ApiException;
import com.animallocket.outbox.OutboxService;
import com.animallocket.user.domain.FriendRequest;
import com.animallocket.user.domain.Friendship;
import com.animallocket.user.domain.UserAccount;
import com.animallocket.user.dto.Dtos;
import com.animallocket.user.repo.BlockRepository;
import com.animallocket.user.repo.FriendRequestRepository;
import com.animallocket.user.repo.FriendshipRepository;
import com.animallocket.user.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FriendService {
    public static final String MATCHED = "MATCHED";
    public static final String REQUESTED = "REQUESTED";
    public static final String ALREADY_FRIENDS = "ALREADY_FRIENDS";
    public static final String ALREADY_REQUESTED = "ALREADY_REQUESTED";
    public static final String BLOCKED = "BLOCKED";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserRepository users;
    private final FriendshipRepository friendships;
    private final FriendRequestRepository requests;
    private final BlockRepository blocks;
    private final EntitlementService entitlements;
    private final OutboxService outbox;

    public FriendService(UserRepository users, FriendshipRepository friendships, FriendRequestRepository requests,
                         BlockRepository blocks, EntitlementService entitlements, OutboxService outbox) {
        this.users = users;
        this.friendships = friendships;
        this.requests = requests;
        this.blocks = blocks;
        this.entitlements = entitlements;
        this.outbox = outbox;
    }

    /**
     * Lõi của "quẹt phải" và lời mời trực tiếp.
     * Nếu bên kia đã gửi lời mời cho mình thì thành bạn ngay (match), nếu không thì tạo lời mời.
     */
    @Transactional
    public Dtos.ConnectResult connect(String from, String to, FriendRequest.Source source) {
        if (from.equals(to)) {
            throw ApiException.badRequest("SELF_REQUEST", "Không thể kết bạn với chính mình");
        }
        UserAccount me = users.findByPublicId(from).filter(UserAccount::isActive)
                .orElseThrow(() -> ApiException.notFound("Người dùng"));
        UserAccount target = users.findByPublicId(to).filter(UserAccount::isActive)
                .orElseThrow(() -> ApiException.notFound("Người dùng"));
        if (blocks.existsEitherWay(from, to)) {
            return new Dtos.ConnectResult(BLOCKED);
        }
        // Phòng thủ chiều sâu: discovery đã lọc theo nhóm tuổi, ở đây chặn thêm một lần nữa.
        if (me.getAgeGroup() != target.getAgeGroup()) {
            throw ApiException.forbidden("Không thể kết nối giữa các nhóm tuổi khác nhau");
        }
        if (areFriends(from, to)) {
            return new Dtos.ConnectResult(ALREADY_FRIENDS);
        }

        Optional<FriendRequest> reverse = requests.findByFromIdAndToId(to, from)
                .filter(r -> r.getStatus() == FriendRequest.Status.PENDING);
        if (reverse.isPresent()) {
            reverse.get().setStatus(FriendRequest.Status.ACCEPTED);
            makeFriends(from, to);
            return new Dtos.ConnectResult(MATCHED);
        }

        Optional<FriendRequest> same = requests.findByFromIdAndToId(from, to);
        if (same.isPresent() && same.get().getStatus() == FriendRequest.Status.PENDING) {
            return new Dtos.ConnectResult(ALREADY_REQUESTED);
        }

        Instant startOfDay = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        long sentToday = requests.countByFromIdAndCreatedAtGreaterThanEqual(from, startOfDay);
        if (sentToday >= entitlements.invitesPerDay(from)) {
            throw ApiException.tooMany("INVITE_LIMIT", "Đã hết lượt gửi lời mời hôm nay");
        }

        // Lời mời cũ đã bị từ chối/huỷ thì tái sử dụng bản ghi (ràng buộc duy nhất from,to).
        FriendRequest fr = same.orElseGet(() -> new FriendRequest(from, to, source));
        fr.setStatus(FriendRequest.Status.PENDING);
        fr.setSource(source);
        fr.setCreatedAt(Instant.now());
        requests.save(fr);
        outbox.publish(Topics.FRIEND, EventTypes.FRIEND_REQUEST_RECEIVED, to,
                new Events.FriendRequestReceived(from, to));
        return new Dtos.ConnectResult(REQUESTED);
    }

    @Transactional
    public void accept(String me, long requestId) {
        FriendRequest r = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("Lời mời"));
        if (!r.getToId().equals(me) || r.getStatus() != FriendRequest.Status.PENDING) {
            throw ApiException.notFound("Lời mời");
        }
        if (blocks.existsEitherWay(r.getFromId(), me)) {
            throw ApiException.notFound("Lời mời");
        }
        r.setStatus(FriendRequest.Status.ACCEPTED);
        makeFriends(r.getFromId(), me);
    }

    @Transactional
    public void decline(String me, long requestId) {
        FriendRequest r = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("Lời mời"));
        if (!r.getToId().equals(me) || r.getStatus() != FriendRequest.Status.PENDING) {
            throw ApiException.notFound("Lời mời");
        }
        r.setStatus(FriendRequest.Status.DECLINED);
    }

    @Transactional
    public void cancel(String me, long requestId) {
        FriendRequest r = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("Lời mời"));
        if (!r.getFromId().equals(me) || r.getStatus() != FriendRequest.Status.PENDING) {
            throw ApiException.notFound("Lời mời");
        }
        r.setStatus(FriendRequest.Status.CANCELED);
    }

    @Transactional
    public void unfriend(String me, String other) {
        friendships.findByUserLowAndUserHigh(min(me, other), max(me, other)).ifPresent(f -> {
            friendships.delete(f);
            // Để post/chat dọn cache bạn bè và đóng hội thoại ngay, thay vì chờ cache hết hạn.
            outbox.publish(Topics.FRIEND, EventTypes.FRIEND_REMOVED, min(me, other),
                    new Events.FriendRemoved(min(me, other), max(me, other)));
        });
    }

    @Transactional(readOnly = true)
    public List<Dtos.PublicProfile> friends(String me) {
        List<String> ids = friendIds(me);
        return profiles(ids).values().stream().toList();
    }

    @Transactional(readOnly = true)
    public List<String> friendIds(String me) {
        return friendships.findAllOf(me).stream().map(f -> f.other(me)).toList();
    }

    @Transactional(readOnly = true)
    public List<Dtos.FriendRequestView> incoming(String me) {
        List<FriendRequest> list = requests.findByToIdAndStatusOrderByCreatedAtDesc(me, FriendRequest.Status.PENDING);
        Map<String, Dtos.PublicProfile> p = profiles(list.stream().map(FriendRequest::getFromId).toList());
        return list.stream().filter(r -> p.containsKey(r.getFromId()))
                .map(r -> new Dtos.FriendRequestView(r.getId(), p.get(r.getFromId()), r.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<Dtos.FriendRequestView> outgoing(String me) {
        List<FriendRequest> list = requests.findByFromIdAndStatusOrderByCreatedAtDesc(me, FriendRequest.Status.PENDING);
        Map<String, Dtos.PublicProfile> p = profiles(list.stream().map(FriendRequest::getToId).toList());
        return list.stream().filter(r -> p.containsKey(r.getToId()))
                .map(r -> new Dtos.FriendRequestView(r.getId(), p.get(r.getToId()), r.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public boolean areFriends(String a, String b) {
        return friendships.findByUserLowAndUserHigh(min(a, b), max(a, b)).isPresent();
    }

    /** Kết bạn trực tiếp, bỏ qua lời mời/giới hạn/nhóm tuổi. Chỉ dùng cho {@link DevBuddy}. Idempotent. */
    @Transactional
    public void forceFriends(String a, String b) {
        makeFriends(a, b);
    }

    private void makeFriends(String a, String b) {
        if (areFriends(a, b)) {
            return;
        }
        friendships.save(new Friendship(a, b));
        outbox.publish(Topics.FRIEND, EventTypes.FRIEND_ACCEPTED, min(a, b),
                new Events.FriendAccepted(min(a, b), max(a, b)));
    }

    private Map<String, Dtos.PublicProfile> profiles(List<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findByPublicIdIn(ids).stream().filter(UserAccount::isActive)
                .collect(Collectors.toMap(UserAccount::getPublicId,
                        Dtos.PublicProfile::of,
                        (x, y) -> x, java.util.LinkedHashMap::new));
    }

    static String min(String a, String b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    static String max(String a, String b) {
        return a.compareTo(b) < 0 ? b : a;
    }
}
