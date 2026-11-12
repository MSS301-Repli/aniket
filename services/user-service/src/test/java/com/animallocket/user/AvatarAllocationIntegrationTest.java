package com.animallocket.user;

import com.animallocket.common.web.ApiException;
import com.animallocket.user.domain.UserAccount;
import com.animallocket.user.domain.UserAvatar;
import com.animallocket.user.repo.AvatarPoolRepository;
import com.animallocket.user.repo.UserRepository;
import com.animallocket.user.service.AvatarAllocator;
import com.animallocket.user.service.AvatarBackfill;
import com.animallocket.user.service.AvatarLibrary;
import com.animallocket.outbox.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Bảo đảm cốt lõi: không bao giờ có hai người cùng một avatar (docs/19-avatar-mechanism.md §4). */
@SpringBootTest
@AutoConfigureMockMvc
class AvatarAllocationIntegrationTest {
    /** Chủ giả để "chiếm" kho trong test; được trả lại ở @AfterEach để không ảnh hưởng test khác. */
    private static final String HOG = "test-hog";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AvatarPoolRepository pool;
    @Autowired UserRepository users;
    @Autowired AvatarAllocator allocator;
    @Autowired AvatarBackfill backfill;
    @Autowired AvatarLibrary library;
    @Autowired OutboxRepository outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @MockBean JwtDecoder jwtDecoder;

    @AfterEach
    void releaseHoggedPool() {
        jdbc.update("UPDATE avatar_pool SET owner_public_id = NULL, assigned_at = NULL WHERE owner_public_id = ?", HOG);
    }

    /** Chiếm toàn bộ kho trừ {@code leave} avatar trống. */
    private void leaveOnlyFree(int leave) {
        List<String> free = jdbc.queryForList(
                "SELECT avatar_key FROM avatar_pool WHERE owner_public_id IS NULL ORDER BY avatar_key", String.class);
        for (String k : free.subList(0, free.size() - leave)) {
            jdbc.update("UPDATE avatar_pool SET owner_public_id = ? WHERE avatar_key = ?", HOG, k);
        }
    }

    private MvcResult register() throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        return mvc.perform(post("/api/v1/users/register").with(jwt().jwt(j -> j.subject(sub)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"birthDate\":\"1995-01-01\"}")).andReturn();
    }

    @Test
    void libraryV1Has3000DistinctAvatarsAndPoolIsSeeded() {
        assertEquals(3000, library.allKeys().size());
        assertTrue(pool.count() >= 3000);
        assertTrue(library.allKeys().keySet().stream().allMatch(k -> k.matches("[a-z]+:\\d:[a-z]+")));
    }

    @Test
    void concurrentRegistrationsNeverShareAnAvatar() throws Exception {
        ExecutorService exec = Executors.newFixedThreadPool(10);
        List<Future<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            futures.add(exec.submit(this::register));
        }
        Set<String> avatars = new HashSet<>();
        for (Future<MvcResult> f : futures) {
            MvcResult r = f.get();
            assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
            avatars.add(json.readTree(r.getResponse().getContentAsString()).get("avatar").asText());
        }
        exec.shutdown();
        assertEquals(40, avatars.size(), "40 người phải có 40 avatar khác nhau");
        // Toàn bộ dữ liệu: không avatar nào xuất hiện hai lần ở bảng users.
        Integer dup = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT avatar_key FROM users WHERE avatar_key IS NOT NULL "
                + "GROUP BY avatar_key HAVING COUNT(*) > 1) d", Integer.class);
        assertEquals(0, dup);
    }

    @Test
    void lastFreeAvatarGoesToExactlyOnePersonThenPoolIsExhausted() throws Exception {
        leaveOnlyFree(1);
        ExecutorService exec = Executors.newFixedThreadPool(2);
        Future<MvcResult> a = exec.submit(this::register);
        Future<MvcResult> b = exec.submit(this::register);
        List<Integer> statuses = List.of(a.get().getResponse().getStatus(), b.get().getResponse().getStatus());
        exec.shutdown();
        assertTrue(statuses.contains(201) && statuses.contains(503), statuses.toString());

        MvcResult third = register();
        assertEquals(503, third.getResponse().getStatus());
        assertEquals("AVATAR_POOL_EXHAUSTED", json.readTree(third.getResponse().getContentAsString()).get("code").asText());
    }

    @Test
    void rolledBackTransactionReturnsAvatarToPool() {
        long before = pool.countByOwnerPublicIdIsNull();
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            allocator.allocate("rollback-user", UserAvatar.Source.REGISTER);
            throw new IllegalStateException("lỗi giữa chừng");
        }));
        assertEquals(before, pool.countByOwnerPublicIdIsNull());
    }

    @Test
    void allocatorRefusesToRunOutsideATransaction() {
        assertThrows(RuntimeException.class, () -> allocator.allocate("x", UserAvatar.Source.REGISTER));
    }

    @Test
    void emptyPoolIsReportedAsExhausted() {
        leaveOnlyFree(0);
        ApiException e = assertThrows(ApiException.class, () -> tx.executeWithoutResult(s ->
                allocator.allocate("nobody", UserAvatar.Source.REGISTER)));
        assertEquals("AVATAR_POOL_EXHAUSTED", e.code());
    }

    @Test
    void backfillGivesOldAccountsDistinctAvatarsAndIsIdempotent() {
        List<String> old = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UserAccount u = new UserAccount();
            u.setPublicId("old" + UUID.randomUUID().toString().replace("-", "").substring(0, 17));
            u.setKeycloakSub("old-sub-" + UUID.randomUUID());
            u.setAlias("Cu " + i);
            u.setLeadAnimal("cat");
            u.setBirthDate(LocalDate.of(1990, 1, 1));
            u.setAgeGroup(UserAccount.AgeGroup.ADULT);
            users.save(u);
            old.add(u.getPublicId());
        }
        UserAccount deleted = new UserAccount();
        deleted.setPublicId("olddeleted" + UUID.randomUUID().toString().substring(0, 10));
        deleted.setKeycloakSub("deleted:x" + UUID.randomUUID());
        deleted.setAlias("deleted");
        deleted.setLeadAnimal("cat");
        deleted.setAgeGroup(UserAccount.AgeGroup.ADULT);
        deleted.setStatus(UserAccount.Status.DELETED);
        users.save(deleted);

        assertTrue(backfill.runOnce() >= 5);
        Set<String> keys = new HashSet<>();
        for (String id : old) {
            UserAccount u = users.findByPublicId(id).orElseThrow();
            assertNotNull(u.getAvatarKey(), id);
            assertEquals(u.getAvatarKey().split(":")[0], u.getLeadAnimal());
            keys.add(u.getAvatarKey());
            assertTrue(outbox.findAll().stream().anyMatch(e -> e.getType().equals("AVATAR_CHANGED") && e.getKey().equals(id)));
        }
        assertEquals(5, keys.size());
        assertNull(users.findByPublicId(deleted.getPublicId()).orElseThrow().getAvatarKey(), "tài khoản đã xoá không lấy avatar");

        String before = users.findByPublicId(old.get(0)).orElseThrow().getAvatarKey();
        assertEquals(0, backfill.runOnce());
        assertEquals(before, users.findByPublicId(old.get(0)).orElseThrow().getAvatarKey());
    }

    @Test
    void deletingAccountKeepsAvatarBoundToIt() throws Exception {
        MvcResult r = register();
        var me = json.readTree(r.getResponse().getContentAsString());
        String id = me.get("publicId").asText();
        String key = me.get("avatar").asText().substring(2);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/users/me")
                .header(com.animallocket.common.Headers.PUBLIC_ID, id).with(jwt()));
        assertEquals(id, jdbc.queryForObject("SELECT owner_public_id FROM avatar_pool WHERE avatar_key = ?", String.class, key));
    }
}
