package com.animallocket.user.service;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Topics;
import com.animallocket.common.event.Events;
import com.animallocket.common.web.ApiException;
import com.animallocket.outbox.OutboxService;
import com.animallocket.user.domain.Block;
import com.animallocket.user.domain.DeviceToken;
import com.animallocket.user.domain.FriendRequest;
import com.animallocket.user.domain.Report;
import com.animallocket.user.domain.UserAccount;
import com.animallocket.user.dto.Dtos;
import com.animallocket.user.repo.BlockRepository;
import com.animallocket.user.repo.DeviceTokenRepository;
import com.animallocket.user.repo.FriendRequestRepository;
import com.animallocket.user.repo.FriendshipRepository;
import com.animallocket.user.repo.ReportRepository;
import com.animallocket.user.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Chặn, báo cáo và thiết bị push. */
@Service
public class SafetyService {
    private static final int MAX_REPORTS_PER_DAY = 20;

    private final UserRepository users;
    private final BlockRepository blocks;
    private final FriendshipRepository friendships;
    private final FriendRequestRepository requests;
    private final ReportRepository reports;
    private final DeviceTokenRepository devices;
    private final OutboxService outbox;

    public SafetyService(UserRepository users, BlockRepository blocks, FriendshipRepository friendships,
                         FriendRequestRepository requests, ReportRepository reports,
                         DeviceTokenRepository devices, OutboxService outbox) {
        this.users = users;
        this.blocks = blocks;
        this.friendships = friendships;
        this.requests = requests;
        this.reports = reports;
        this.devices = devices;
        this.outbox = outbox;
    }

    /** Chặn có hiệu lực hai chiều: cắt bạn bè, huỷ lời mời, phát USER_BLOCKED để chat/post/discovery cắt ngay. */
    @Transactional
    public void block(String me, String target) {
        if (me.equals(target)) {
            throw ApiException.badRequest("SELF_BLOCK", "Không thể chặn chính mình");
        }
        users.findByPublicId(target).filter(UserAccount::isActive)
                .orElseThrow(() -> ApiException.notFound("Người dùng"));
        if (blocks.existsByBlockerIdAndBlockedId(me, target)) {
            return;
        }
        blocks.save(new Block(me, target));
        String low = me.compareTo(target) < 0 ? me : target;
        String high = me.compareTo(target) < 0 ? target : me;
        friendships.findByUserLowAndUserHigh(low, high).ifPresent(friendships::delete);
        cancelPending(me, target);
        cancelPending(target, me);
        outbox.publish(Topics.USER, EventTypes.USER_BLOCKED, me, new Events.UserBlocked(me, target));
    }

    @Transactional
    public void unblock(String me, String target) {
        blocks.deleteByBlockerIdAndBlockedId(me, target);
    }

    @Transactional(readOnly = true)
    public List<Dtos.PublicProfile> blocked(String me) {
        List<String> ids = blocks.findByBlockerId(me).stream().map(Block::getBlockedId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return users.findByPublicIdIn(ids).stream().filter(UserAccount::isActive)
                .map(Dtos.PublicProfile::of).toList();
    }

    @Transactional
    public void report(String me, Dtos.ReportRequest req) {
        long today = reports.countByReporterIdAndCreatedAtGreaterThanEqual(me, Instant.now().minus(Duration.ofDays(1)));
        if (today >= MAX_REPORTS_PER_DAY) {
            throw ApiException.tooMany("REPORT_LIMIT", "Đã vượt số báo cáo cho phép trong ngày");
        }
        Report r = new Report();
        r.setReporterId(me);
        r.setTargetPublicId(req.targetPublicId());
        r.setTargetType(req.targetType());
        r.setTargetRef(req.targetRef());
        r.setReason(req.reason());
        r.setDetails(req.details());
        reports.save(r);
    }

    @Transactional
    public void registerDevice(String me, Dtos.DeviceRequest req) {
        DeviceToken d = devices.findByToken(req.token()).orElseGet(DeviceToken::new);
        d.setToken(req.token());
        d.setPublicId(me);
        d.setPlatform(req.platform());
        d.setUpdatedAt(Instant.now());
        devices.save(d);
    }

    @Transactional
    public void removeDevice(String me, String token) {
        devices.deleteByTokenAndPublicId(token, me);
    }

    private void cancelPending(String from, String to) {
        requests.findByFromIdAndToId(from, to)
                .filter(r -> r.getStatus() == FriendRequest.Status.PENDING)
                .ifPresent(r -> r.setStatus(FriendRequest.Status.CANCELED));
    }
}
