package com.animallocket.user.service;

import com.animallocket.common.EventTypes;
import com.animallocket.common.PublicIds;
import com.animallocket.common.Topics;
import com.animallocket.common.event.Events;
import com.animallocket.common.web.ApiException;
import com.animallocket.outbox.OutboxService;
import com.animallocket.user.domain.Animal;
import com.animallocket.user.domain.UserAccount;
import com.animallocket.user.domain.UserAnimal;
import com.animallocket.user.domain.UserAvatar;
import com.animallocket.user.dto.Dtos;
import com.animallocket.user.repo.AnimalRepository;
import com.animallocket.user.repo.BlockRepository;
import com.animallocket.user.repo.DeviceTokenRepository;
import com.animallocket.user.repo.EntitlementRepository;
import com.animallocket.user.repo.FriendRequestRepository;
import com.animallocket.user.repo.FriendshipRepository;
import com.animallocket.user.repo.SpinLogRepository;
import com.animallocket.user.repo.UserAnimalRepository;
import com.animallocket.user.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AccountService {
    private static final Duration ALIAS_COOLDOWN = Duration.ofHours(24);

    private final UserRepository users;
    private final AnimalRepository animals;
    private final UserAnimalRepository userAnimals;
    private final SpinLogRepository spinLogs;
    private final FriendshipRepository friendships;
    private final FriendRequestRepository requests;
    private final BlockRepository blocks;
    private final DeviceTokenRepository devices;
    private final EntitlementRepository entitlementRepo;
    private final AnimalCatalog catalog;
    private final EntitlementService entitlements;
    private final OutboxService outbox;
    private final IdentityProviderClient identityProvider;
    private final DevBuddy devBuddy;
    private final AvatarAllocator avatarAllocator;
    private final AvatarLibrary avatarLibrary;

    public AccountService(UserRepository users, AnimalRepository animals, UserAnimalRepository userAnimals,
                          SpinLogRepository spinLogs, FriendshipRepository friendships,
                          FriendRequestRepository requests, BlockRepository blocks,
                          DeviceTokenRepository devices, EntitlementRepository entitlementRepo,
                          AnimalCatalog catalog, EntitlementService entitlements, OutboxService outbox,
                          IdentityProviderClient identityProvider, DevBuddy devBuddy,
                          AvatarAllocator avatarAllocator, AvatarLibrary avatarLibrary) {
        this.users = users;
        this.animals = animals;
        this.userAnimals = userAnimals;
        this.spinLogs = spinLogs;
        this.friendships = friendships;
        this.requests = requests;
        this.blocks = blocks;
        this.devices = devices;
        this.entitlementRepo = entitlementRepo;
        this.catalog = catalog;
        this.entitlements = entitlements;
        this.outbox = outbox;
        this.identityProvider = identityProvider;
        this.devBuddy = devBuddy;
        this.avatarAllocator = avatarAllocator;
        this.avatarLibrary = avatarLibrary;
    }

    /** Đăng ký lần đầu sau khi đăng nhập Keycloak. Idempotent theo sub. */
    @Transactional
    public Dtos.MeProfile register(String sub, Dtos.RegisterRequest req) {
        Optional<UserAccount> existing = users.findByKeycloakSub(sub);
        if (existing.isPresent()) {
            return toMe(existing.get());
        }
        LocalDate today = LocalDate.now();
        if (req.birthDate().isAfter(today)) {
            throw ApiException.badRequest("INVALID_BIRTHDATE", "Ngày sinh không hợp lệ");
        }
        int age = Period.between(req.birthDate(), today).getYears();
        if (age > 120) {
            throw ApiException.badRequest("INVALID_BIRTHDATE", "Ngày sinh không hợp lệ");
        }
        String alias = req.alias() != null ? AliasPolicy.validate(req.alias()) : null;

        UserAccount u = new UserAccount();
        u.setPublicId(PublicIds.generate());
        // Quay đúng một lần: avatar chưa ai sở hữu, cấp trong cùng transaction (lỗi phía sau → avatar về kho).
        AvatarLibrary.Avatar avatar = avatarAllocator.allocate(u.getPublicId(), UserAvatar.Source.REGISTER);
        u.setKeycloakSub(sub);
        u.setAlias(alias != null ? alias : AliasPolicy.generate(avatarLibrary.animalName(avatar.animal())));
        u.setLeadAnimal(avatar.animal());
        u.setAvatarKey(avatar.key());
        u.setAvatarVersion(avatar.version());
        u.setBirthDate(req.birthDate());
        u.setAgeGroup(age < 18 ? UserAccount.AgeGroup.MINOR : UserAccount.AgeGroup.ADULT);
        // Tài khoản dưới 18 mặc định KHÔNG bật khám phá; người lớn cũng tắt cho đến khi chủ động bật.
        u.setDiscoverable(false);
        users.save(u);

        outbox.publish(Topics.USER, EventTypes.USER_REGISTERED, u.getPublicId(),
                new Events.UserRegistered(u.getPublicId(), u.getAgeGroup().name(), u.getLeadAnimal(), u.avatarCode()));
        devBuddy.befriend(u.getPublicId()); // chỉ khi dev bật animal.dev-buddy.public-id
        return toMe(u);
    }

    @Transactional(readOnly = true)
    public Dtos.MeProfile me(String publicId) {
        return toMe(requireActive(publicId));
    }

    @Transactional
    public Dtos.MeProfile update(String publicId, Dtos.UpdateProfileRequest req) {
        UserAccount u = requireActive(publicId);
        if (req.alias() != null && !req.alias().equals(u.getAlias())) {
            Instant last = u.getAliasChangedAt();
            if (last != null && last.plus(ALIAS_COOLDOWN).isAfter(Instant.now())) {
                throw ApiException.tooMany("ALIAS_COOLDOWN", "Chỉ được đổi alias 1 lần mỗi 24 giờ");
            }
            u.setAlias(AliasPolicy.validate(req.alias()));
            u.setAliasChangedAt(Instant.now());
        }
        if (req.discoverable() != null) {
            if (u.isDiscoverable() && !req.discoverable()) {
                // Tắt khám phá phải xoá vị trí đang lưu ở discovery ngay lập tức (riêng tư), không chờ TTL 30 phút.
                outbox.publish(Topics.USER, EventTypes.DISCOVERY_DISABLED, publicId, new Events.DiscoveryDisabled(publicId));
            }
            u.setDiscoverable(req.discoverable());
        }
        return toMe(u);
    }

    @Transactional(readOnly = true)
    public Dtos.PublicProfile publicProfile(String viewer, String target) {
        UserAccount t = users.findByPublicId(target).filter(UserAccount::isActive)
                .orElseThrow(() -> ApiException.notFound("Người dùng"));
        if (blocks.existsEitherWay(viewer, target)) {
            throw ApiException.notFound("Người dùng"); // không tiết lộ việc bị chặn
        }
        return Dtos.PublicProfile.of(t);
    }

    /**
     * Đã ngừng: con vật hiển thị giờ là avatar duy nhất cấp khi đăng ký (docs/19-avatar-mechanism.md).
     * Đổi avatar trong bộ sưu tập sẽ làm cùng phần nạp tiền.
     */
    public Dtos.MeProfile setLeadAnimal(String publicId, String animalCode) {
        throw retired("Không còn đổi con vật chủ đạo; con vật hiển thị là avatar của bạn");
    }

    @Transactional(readOnly = true)
    public List<Dtos.AnimalView> myAnimals(String publicId) {
        Set<String> owned = userAnimals.findByPublicId(publicId).stream()
                .map(UserAnimal::getAnimalCode).collect(Collectors.toSet());
        return animals.findAllById(owned).stream().map(Dtos.AnimalView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<Dtos.AnimalView> catalog() {
        return animals.findAll().stream().map(Dtos.AnimalView::of).toList();
    }

    /** Đã ngừng: mỗi người chỉ quay một lần lúc đăng ký; quay thêm sẽ gắn với nạp tiền. */
    public Dtos.SpinResult spin(String publicId) {
        throw retired("Quay con vật miễn phí đã ngừng; avatar được quay một lần khi đăng ký");
    }

    private static ApiException retired(String message) {
        return new ApiException(org.springframework.http.HttpStatus.GONE, "FEATURE_RETIRED", message);
    }

    /** Thêm các con vật của một gói vào bộ sưu tập (từ event CONSUMABLE_GRANTED). Idempotent. */
    @Transactional
    public void grantPack(String publicId, String packCode) {
        if (users.findByPublicId(publicId).filter(UserAccount::isActive).isEmpty()) {
            return;
        }
        for (Animal a : animals.findByPackCode(packCode)) {
            if (!userAnimals.existsByPublicIdAndAnimalCode(publicId, a.getCode())) {
                userAnimals.save(new UserAnimal(publicId, a.getCode()));
            }
        }
    }

    /**
     * Xoá tài khoản: xoá dữ liệu cá nhân, ẩn danh bản ghi, phát USER_DELETED để mọi service dọn dữ liệu.
     * Gọi Keycloak trước khi commit: nếu lỗi thì rollback toàn bộ và người dùng thử lại (fail closed).
     */
    @Transactional
    public void delete(String publicId) {
        UserAccount u = users.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("Người dùng"));
        if (u.getStatus() == UserAccount.Status.DELETED) {
            return;
        }
        String sub = u.getKeycloakSub();
        friendships.deleteAllOf(publicId);
        requests.deleteAllOf(publicId);
        blocks.deleteAllOf(publicId);
        devices.deleteAllOf(publicId);
        userAnimals.deleteAllOf(publicId);
        spinLogs.deleteAllOf(publicId);
        entitlementRepo.deleteById(publicId);
        u.setStatus(UserAccount.Status.DELETED);
        u.setKeycloakSub("deleted:" + publicId);
        u.setAlias("deleted");
        u.setBirthDate(null);
        u.setDiscoverable(false);
        outbox.publish(Topics.USER, EventTypes.USER_DELETED, publicId, new Events.UserDeleted(publicId));
        identityProvider.deleteUser(sub);
    }

    public UserAccount requireActive(String publicId) {
        return users.findByPublicId(publicId).filter(UserAccount::isActive)
                .orElseThrow(() -> ApiException.notFound("Người dùng"));
    }

    private Dtos.MeProfile toMe(UserAccount u) {
        return new Dtos.MeProfile(u.getPublicId(), u.getAlias(), u.getLeadAnimal(), u.avatarCode(),
                u.getAgeGroup().name(),
                u.isDiscoverable(), entitlements.plan(u.getPublicId()), u.getCreatedAt());
    }
}
