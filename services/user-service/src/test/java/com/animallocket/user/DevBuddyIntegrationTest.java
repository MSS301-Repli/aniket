package com.animallocket.user;

import com.animallocket.outbox.OutboxRepository;
import com.animallocket.user.domain.UserAccount;
import com.animallocket.user.repo.UserRepository;
import com.animallocket.user.service.DevBuddy;
import com.animallocket.user.service.FriendService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "animal.dev-buddy.public-id=" + DevBuddyIntegrationTest.BUDDY,
        // DB riêng: context khác UserFlowIntegrationTest, tránh create-drop xoá bảng của nhau.
        "spring.datasource.url=jdbc:h2:mem:userdb_buddy;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class DevBuddyIntegrationTest {
    static final String BUDDY = "buddybuddybuddybuddy";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired FriendService friends;
    @Autowired DevBuddy devBuddy;
    @Autowired OutboxRepository outbox;
    @MockBean JwtDecoder jwtDecoder;

    @BeforeEach
    void ensureBuddy() {
        if (users.findByPublicId(BUDDY).isEmpty()) {
            users.save(account(BUDDY, UserAccount.AgeGroup.ADULT));
        }
    }

    private static UserAccount account(String publicId, UserAccount.AgeGroup group) {
        UserAccount u = new UserAccount();
        u.setPublicId(publicId);
        u.setKeycloakSub("sub-" + UUID.randomUUID());
        u.setAlias("bot");
        u.setLeadAnimal("panda");
        u.setBirthDate(LocalDate.of(2000, 1, 1));
        u.setAgeGroup(group);
        u.setDiscoverable(false);
        return u;
    }

    private String register(String birthDate) throws Exception {
        String body = mvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j.subject("sub-" + UUID.randomUUID())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthDate\":\"" + birthDate + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("publicId").asText();
    }

    @Test
    void newAccountsOfAnyAgeGroupBecomeFriendsWithBuddy() throws Exception {
        String adult = register("2000-01-01");
        String minor = register(LocalDate.now().minusYears(15).toString());
        assertTrue(friends.areFriends(BUDDY, adult));
        assertTrue(friends.areFriends(BUDDY, minor));
        assertTrue(outbox.findAll().stream().anyMatch(e -> e.getType().equals("FRIEND_ACCEPTED")
                && e.getPayload().contains(adult)), "chat-service cần FRIEND_ACCEPTED để xoá cache quan hệ");
    }

    @Test
    void backfillBefriendsExistingAccountsOnly() {
        users.save(account("oldoldoldoldoldold22", UserAccount.AgeGroup.ADULT));
        UserAccount gone = account("gonegonegonegonegone", UserAccount.AgeGroup.ADULT);
        gone.setStatus(UserAccount.Status.DELETED);
        users.save(gone);

        devBuddy.backfill();
        devBuddy.backfill(); // idempotent

        assertTrue(friends.areFriends(BUDDY, "oldoldoldoldoldold22"));
        assertFalse(friends.areFriends(BUDDY, "gonegonegonegonegone"));
        assertFalse(friends.areFriends(BUDDY, BUDDY));
    }
}
