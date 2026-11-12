package com.animallocket.user;

import com.animallocket.common.Headers;
import com.animallocket.outbox.OutboxEvent;
import com.animallocket.outbox.OutboxRepository;
import com.animallocket.user.domain.Entitlement;
import com.animallocket.user.repo.EntitlementRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OutboxRepository outbox;
    @Autowired EntitlementRepository entitlements;
    @MockBean JwtDecoder jwtDecoder;

    private String register(String birthDate) throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        MvcResult r = mvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j.subject(sub)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthDate\":\"" + birthDate + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("publicId").asText();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String publicId) {
        return b.header(Headers.PUBLIC_ID, publicId).with(jwt()).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void registerIsIdempotentAndNeverLeaksSub() throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        var req = post("/api/v1/users/register").with(jwt().jwt(j -> j.subject(sub)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"birthDate\":\"2000-01-01\"}");
        MvcResult first = mvc.perform(req).andExpect(status().isCreated()).andReturn();
        MvcResult second = mvc.perform(req).andExpect(status().isCreated()).andReturn();
        assertEquals(body(first).get("publicId").asText(), body(second).get("publicId").asText());
        String raw = first.getResponse().getContentAsString();
        assertFalse(raw.contains(sub), "không được lộ sub");
        assertFalse(raw.contains("2000-01-01"), "không được lộ ngày sinh");
        assertEquals("ADULT", body(first).get("ageGroup").asText());
        assertFalse(body(first).get("discoverable").asBoolean());
    }

    @Test
    void concurrentRegistrationsOfTheSameAccountConvergeOnOneProfile() throws Exception {
        String sub = "sub-race-" + UUID.randomUUID();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        List<java.util.concurrent.Future<String>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            results.add(pool.submit(() -> {
                MvcResult r = mvc.perform(post("/api/v1/users/register").with(jwt().jwt(j -> j.subject(sub)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"birthDate\":\"1999-01-01\"}")).andReturn();
                assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
                return json.readTree(r.getResponse().getContentAsString()).get("publicId").asText();
            }));
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (var f : results) {
            ids.add(f.get());
        }
        pool.shutdown();
        assertEquals(1, ids.size(), "mọi request phải trả về cùng một hồ sơ, không ai nhận 500");
    }

    @Test
    void registerEmitsUserRegisteredEvent() throws Exception {
        String pid = register("2000-01-01");
        List<OutboxEvent> events = outbox.findAll();
        assertTrue(events.stream().anyMatch(e -> e.getType().equals("USER_REGISTERED") && e.getKey().equals(pid)));
    }

    @Test
    void minorsAreClassifiedByBirthDate() throws Exception {
        String birth = java.time.LocalDate.now().minusYears(15).toString();
        String sub = "sub-" + UUID.randomUUID();
        mvc.perform(post("/api/v1/users/register").with(jwt().jwt(j -> j.subject(sub)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"birthDate\":\"" + birth + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ageGroup").value("MINOR"));
    }

    @Test
    void rejectsFutureBirthDate() throws Exception {
        mvc.perform(post("/api/v1/users/register").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"birthDate\":\"2999-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestsWithoutJwtAreRejected() throws Exception {
        mvc.perform(get("/api/v1/users/me").header(Headers.PUBLIC_ID, "x")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingPublicIdMeansProfileRequired() throws Exception {
        mvc.perform(get("/api/v1/users/me").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
    }

    @Test
    void mutualSwipeMakesFriendsAndEmitsEvent() throws Exception {
        String a = register("1999-05-05");
        String b = register("1998-06-06");
        String swipeAtoB = "{\"from\":\"" + a + "\",\"to\":\"" + b + "\"}";
        String swipeBtoA = "{\"from\":\"" + b + "\",\"to\":\"" + a + "\"}";

        mvc.perform(post("/internal/swipes/right").header(Headers.INTERNAL_TOKEN, "dev-internal-token")
                        .contentType(MediaType.APPLICATION_JSON).content(swipeAtoB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("REQUESTED"));
        mvc.perform(post("/internal/swipes/right").header(Headers.INTERNAL_TOKEN, "dev-internal-token")
                        .contentType(MediaType.APPLICATION_JSON).content(swipeBtoA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("MATCHED"));
        // Lặp lại không tạo thêm bạn.
        mvc.perform(post("/internal/swipes/right").header(Headers.INTERNAL_TOKEN, "dev-internal-token")
                        .contentType(MediaType.APPLICATION_JSON).content(swipeBtoA))
                .andExpect(jsonPath("$.result").value("ALREADY_FRIENDS"));

        mvc.perform(as(get("/api/v1/friends"), a)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].publicId").value(b));
        long accepted = outbox.findAll().stream().filter(e -> e.getType().equals("FRIEND_ACCEPTED")).count();
        assertTrue(accepted >= 1);
    }

    @Test
    void unfriendEmitsFriendRemovedOnlyWhenAFriendshipExisted() throws Exception {
        String a = register("1995-01-01");
        String b = register("1995-01-01");
        String ab = "{\"from\":\"" + a + "\",\"to\":\"" + b + "\"}";
        String ba = "{\"from\":\"" + b + "\",\"to\":\"" + a + "\"}";
        for (String body : List.of(ab, ba)) {
            mvc.perform(post("/internal/swipes/right").header(Headers.INTERNAL_TOKEN, "dev-internal-token")
                    .contentType(MediaType.APPLICATION_JSON).content(body));
        }
        mvc.perform(as(delete("/api/v1/friends/" + b), a)).andExpect(status().isNoContent());
        mvc.perform(as(delete("/api/v1/friends/" + b), a)).andExpect(status().isNoContent()); // lặp lại: không phát thêm
        long removed = outbox.findAll().stream().filter(e -> e.getType().equals("FRIEND_REMOVED")).count();
        assertEquals(1, removed);
        mvc.perform(as(get("/api/v1/friends"), b)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void disablingDiscoveryEmitsEventOnlyOnTrueToFalseTransition() throws Exception {
        String a = register("1995-01-01");
        java.util.function.Supplier<Long> count = () -> outbox.findAll().stream()
                .filter(e -> e.getType().equals("DISCOVERY_DISABLED") && e.getKey().equals(a)).count();

        mvc.perform(as(patch("/api/v1/users/me"), a).content("{\"discoverable\":false}")).andExpect(status().isOk());
        assertEquals(0L, count.get(), "đang tắt sẵn thì không cần phát event");
        mvc.perform(as(patch("/api/v1/users/me"), a).content("{\"discoverable\":true}")).andExpect(status().isOk());
        assertEquals(0L, count.get());
        mvc.perform(as(patch("/api/v1/users/me"), a).content("{\"discoverable\":false}")).andExpect(status().isOk());
        assertEquals(1L, count.get(), "bật rồi tắt phải phát DISCOVERY_DISABLED để discovery xoá vị trí");
    }

    @Test
    void minorAndAdultCannotConnect() throws Exception {
        String adult = register("1990-01-01");
        String minor = register(java.time.LocalDate.now().minusYears(14).toString());
        mvc.perform(post("/internal/swipes/right").header(Headers.INTERNAL_TOKEN, "dev-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"" + adult + "\",\"to\":\"" + minor + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void dailyInviteLimitIsEnforced() throws Exception {
        String a = register("1995-01-01");
        for (int i = 0; i < 3; i++) {
            String other = register("1995-01-01");
            mvc.perform(as(post("/api/v1/friends/requests"), a).content("{\"toPublicId\":\"" + other + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("REQUESTED"));
        }
        String extra = register("1995-01-01");
        mvc.perform(as(post("/api/v1/friends/requests"), a).content("{\"toPublicId\":\"" + extra + "\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("INVITE_LIMIT"));
    }

    @Test
    void acceptIncomingRequest() throws Exception {
        String a = register("1995-01-01");
        String b = register("1995-01-01");
        mvc.perform(as(post("/api/v1/friends/requests"), a).content("{\"toPublicId\":\"" + b + "\"}"))
                .andExpect(status().isOk());
        MvcResult incoming = mvc.perform(as(get("/api/v1/friends/requests/incoming"), b))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andReturn();
        long id = body(incoming).get(0).get("id").asLong();
        // Người khác không được chấp nhận hộ.
        mvc.perform(as(post("/api/v1/friends/requests/" + id + "/accept"), a)).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/v1/friends/requests/" + id + "/accept"), b)).andExpect(status().isNoContent());
        mvc.perform(as(get("/api/v1/friends"), a)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void blockRemovesFriendshipAndHidesProfile() throws Exception {
        String a = register("1995-01-01");
        String b = register("1995-01-01");
        mvc.perform(post("/internal/swipes/right").header(Headers.INTERNAL_TOKEN, "dev-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"" + a + "\",\"to\":\"" + b + "\"}"));
        mvc.perform(post("/internal/swipes/right").header(Headers.INTERNAL_TOKEN, "dev-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"" + b + "\",\"to\":\"" + a + "\"}"));

        mvc.perform(as(put("/api/v1/blocks/" + b), a)).andExpect(status().isNoContent());

        mvc.perform(as(get("/api/v1/friends"), a)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(get("/api/v1/friends"), b)).andExpect(jsonPath("$.length()").value(0));
        // Hai chiều: b cũng không xem được hồ sơ của a.
        mvc.perform(as(get("/api/v1/users/" + a), b)).andExpect(status().isNotFound());
        mvc.perform(post("/internal/swipes/right").header(Headers.INTERNAL_TOKEN, "dev-internal-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"" + b + "\",\"to\":\"" + a + "\"}"))
                .andExpect(jsonPath("$.result").value("BLOCKED"));
        assertTrue(outbox.findAll().stream().anyMatch(e -> e.getType().equals("USER_BLOCKED") && e.getKey().equals(a)));
    }

    @Test
    void registerGivesAvatarThatShowsEverywhere() throws Exception {
        String a = register("1995-01-01");
        String b = register("1995-01-01");
        JsonNode me = body(mvc.perform(as(get("/api/v1/users/me"), a)).andExpect(status().isOk()).andReturn());
        String avatar = me.get("avatar").asText();
        assertTrue(avatar.matches("1:[a-z]+:[0-9]:[a-z]+"), avatar);
        assertEquals(avatar.split(":")[1], me.get("leadAnimal").asText(), "leadAnimal = con vật của avatar");

        mvc.perform(as(get("/api/v1/users/" + a), b)).andExpect(jsonPath("$.avatar").value(avatar));
        mvc.perform(get("/internal/users/" + a).header(Headers.INTERNAL_TOKEN, "dev-internal-token"))
                .andExpect(jsonPath("$.avatar").value(avatar));
        mvc.perform(as(get("/api/v1/users/me/avatars"), a)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].avatar").value(avatar)).andExpect(jsonPath("$[0].source").value("REGISTER"));
        assertTrue(outbox.findAll().stream().anyMatch(e -> e.getType().equals("USER_REGISTERED")
                && e.getKey().equals(a) && e.getPayload().contains(avatar)));
    }

    @Test
    void oldFreeSpinAndLeadAnimalSwitchAreRetired() throws Exception {
        String a = register("1995-01-01");
        mvc.perform(as(post("/api/v1/users/me/spin"), a)).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("FEATURE_RETIRED"));
        mvc.perform(as(put("/api/v1/users/me/lead-animal"), a).content("{\"animalCode\":\"cat\"}"))
                .andExpect(status().isGone());
    }

    @Test
    void avatarLibraryIsServedAndCacheable() throws Exception {
        mvc.perform(get("/api/v1/animals/avatar-lib/1").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.palettes.length()").value(10))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")));
        mvc.perform(get("/api/v1/animals/avatar-lib/99").with(jwt())).andExpect(status().isNotFound());
    }

    @Test
    void aliasValidationAndCooldown() throws Exception {
        String a = register("1995-01-01");
        mvc.perform(as(patch("/api/v1/users/me"), a).content("{\"alias\":\"Admin Official\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ALIAS"));
        mvc.perform(as(patch("/api/v1/users/me"), a).content("{\"alias\":\"Mèo Con\",\"discoverable\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alias").value("Mèo Con"))
                .andExpect(jsonPath("$.discoverable").value(true));
        mvc.perform(as(patch("/api/v1/users/me"), a).content("{\"alias\":\"Tên Khác\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void deleteAccountWipesDataAndEmitsEvent() throws Exception {
        String a = register("1995-01-01");
        String b = register("1995-01-01");
        mvc.perform(as(post("/api/v1/friends/requests"), a).content("{\"toPublicId\":\"" + b + "\"}"));
        mvc.perform(as(delete("/api/v1/users/me"), a)).andExpect(status().isNoContent());
        mvc.perform(as(get("/api/v1/users/me"), a)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/v1/friends/requests/incoming"), b)).andExpect(jsonPath("$.length()").value(0));
        assertTrue(outbox.findAll().stream().anyMatch(e -> e.getType().equals("USER_DELETED") && e.getKey().equals(a)));
    }

    @Test
    void internalEndpointsRequireToken() throws Exception {
        mvc.perform(get("/internal/identity/whatever")).andExpect(status().isForbidden());
        mvc.perform(get("/internal/identity/unknown-sub").header(Headers.INTERNAL_TOKEN, "dev-internal-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void identityLookupMapsSubToPublicId() throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        MvcResult r = mvc.perform(post("/api/v1/users/register").with(jwt().jwt(j -> j.subject(sub)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"birthDate\":\"1990-01-01\"}")).andReturn();
        String pid = body(r).get("publicId").asText();
        mvc.perform(get("/internal/identity/" + sub).header(Headers.INTERNAL_TOKEN, "dev-internal-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publicId").value(pid));
    }
}
