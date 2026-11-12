package com.animallocket.user.web;

import com.animallocket.common.web.ApiException;
import com.animallocket.user.domain.Block;
import com.animallocket.user.domain.DeviceToken;
import com.animallocket.user.domain.FriendRequest;
import com.animallocket.user.domain.UserAccount;
import com.animallocket.user.dto.Dtos;
import com.animallocket.user.repo.BlockRepository;
import com.animallocket.user.repo.DeviceTokenRepository;
import com.animallocket.user.repo.UserRepository;
import com.animallocket.user.service.EntitlementService;
import com.animallocket.user.service.FriendService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** API nội bộ cho service khác (được bảo vệ bằng InternalTokenFilter, gateway không route tới đây). */
@RestController
@RequestMapping("/internal")
public class InternalController {
    private final UserRepository users;
    private final BlockRepository blocks;
    private final DeviceTokenRepository devices;
    private final FriendService friends;
    private final EntitlementService entitlements;

    public InternalController(UserRepository users, BlockRepository blocks, DeviceTokenRepository devices,
                              FriendService friends, EntitlementService entitlements) {
        this.users = users;
        this.blocks = blocks;
        this.devices = devices;
        this.friends = friends;
        this.entitlements = entitlements;
    }

    /** Gateway tra sub → publicId. 404 nếu chưa đăng ký hồ sơ. */
    @GetMapping("/identity/{sub}")
    public Dtos.IdentityResponse identity(@PathVariable String sub) {
        return users.findByKeycloakSub(sub).filter(UserAccount::isActive)
                .map(u -> new Dtos.IdentityResponse(u.getPublicId()))
                .orElseThrow(() -> ApiException.notFound("Hồ sơ"));
    }

    @GetMapping("/users/{publicId}")
    public Dtos.InternalUser user(@PathVariable String publicId) {
        return users.findByPublicId(publicId).map(this::toInternal)
                .orElseThrow(() -> ApiException.notFound("Người dùng"));
    }

    @GetMapping("/users")
    public List<Dtos.InternalUser> batch(@RequestParam List<String> ids) {
        return users.findByPublicIdIn(ids).stream().map(this::toInternal).toList();
    }

    @GetMapping("/friends/{publicId}")
    public List<String> friendIds(@PathVariable String publicId) {
        return friends.friendIds(publicId);
    }

    @GetMapping("/relationship")
    public Dtos.Relationship relationship(@RequestParam String a, @RequestParam String b) {
        return new Dtos.Relationship(friends.areFriends(a, b), blocks.existsEitherWay(a, b));
    }

    /** Danh sách cần loại khỏi bộ thẻ quẹt: bạn bè và người chặn/bị chặn. */
    @GetMapping("/exclusions/{publicId}")
    public Dtos.Exclusions exclusions(@PathVariable String publicId) {
        Set<String> blocked = new HashSet<>();
        blocks.findByBlockerId(publicId).forEach(b -> blocked.add(b.getBlockedId()));
        blocks.findByBlockedId(publicId).stream().map(Block::getBlockerId).forEach(blocked::add);
        return new Dtos.Exclusions(friends.friendIds(publicId), List.copyOf(blocked));
    }

    /** Discovery gọi khi quẹt phải. Trả MATCHED nếu thành bạn ngay. */
    @PostMapping("/swipes/right")
    public Dtos.ConnectResult swipeRight(@Valid @RequestBody Dtos.SwipeRequest req) {
        return friends.connect(req.from(), req.to(), FriendRequest.Source.SWIPE);
    }

    @GetMapping("/devices/{publicId}")
    public List<String> deviceTokens(@PathVariable String publicId) {
        return devices.findByPublicId(publicId).stream().map(DeviceToken::getToken).toList();
    }

    /** notification-service gỡ token FCM đã chết. */
    @DeleteMapping("/devices/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void removeDeadToken(@PathVariable String token) {
        devices.findByToken(token).ifPresent(devices::delete);
    }

    private Dtos.InternalUser toInternal(UserAccount u) {
        return new Dtos.InternalUser(u.getPublicId(), u.getAlias(), u.getLeadAnimal(), u.avatarCode(),
                u.getAgeGroup().name(),
                u.isDiscoverable(), u.isActive(), entitlements.isPro(u.getPublicId()));
    }
}
