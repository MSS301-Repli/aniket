package com.animallocket.chat;

import com.animallocket.chat.client.Gateways;
import com.animallocket.common.Headers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chạy với MongoDB thật qua Testcontainers. Tự bỏ qua khi máy không có Docker
 * (kiểm tra lại khi có Docker: mvn -pl services/chat-service test).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // bean webSocketContainer cần servlet container thật
@AutoConfigureMockMvc
class ChatMongoIntegrationTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String BOB = "bbbbbbbbbbbbbbbbbbbb";

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", () -> mongo.getReplicaSetUrl("chat_test"));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean Gateways.UserGateway users;
    @MockBean Gateways.MediaGateway media;
    @MockBean Gateways.EventPublisher events;
    @MockBean org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Test
    void openSendAndReadHistoryAgainstRealMongo() throws Exception {
        when(users.relationship(anyString(), anyString())).thenReturn(new Gateways.Relationship(true, false));

        String body = mvc.perform(post("/api/v1/chat/conversations").header(Headers.PUBLIC_ID, ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"withPublicId\":\"" + BOB + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String cid = json.readTree(body).get("id").asText();

        mvc.perform(post("/api/v1/chat/conversations/" + cid + "/messages").header(Headers.PUBLIC_ID, ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"xin chào\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/chat/conversations/" + cid + "/messages").header(Headers.PUBLIC_ID, BOB))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].text").value("xin chào"));
        mvc.perform(get("/api/v1/chat/conversations").header(Headers.PUBLIC_ID, BOB))
                .andExpect(jsonPath("$[0].unread").value(1));
    }
}
