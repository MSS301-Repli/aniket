package com.animallocket.notification;

import com.animallocket.common.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserServiceDeviceDirectoryTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final UserServiceDeviceDirectory directory =
            new UserServiceDeviceDirectory(builder, "http://user-service", "internal-secret");

    @Test
    void tokensCallsInternalDevicesWithInternalToken() {
        server.expect(requestTo("http://user-service/internal/devices/u1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(Headers.INTERNAL_TOKEN, "internal-secret"))
                .andRespond(withSuccess("[\"tok1\",\"tok2\"]", MediaType.APPLICATION_JSON));
        assertEquals(List.of("tok1", "tok2"), directory.tokens("u1"));
        server.verify();
    }

    @Test
    void friendIdsCallsInternalFriends() {
        server.expect(requestTo("http://user-service/internal/friends/u1"))
                .andRespond(withSuccess("[\"f1\"]", MediaType.APPLICATION_JSON));
        assertEquals(List.of("f1"), directory.friendIds("u1"));
        server.verify();
    }

    @Test
    void emptyBodyMeansNoDevices() {
        server.expect(requestTo("http://user-service/internal/devices/u2")).andRespond(withNoContent());
        assertEquals(List.of(), directory.tokens("u2"));
    }

    @Test
    void removeTokenDeletesTheDevice() {
        server.expect(requestTo("http://user-service/internal/devices/dead"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());
        directory.removeToken("dead");
        server.verify();
    }
}
