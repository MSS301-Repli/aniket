package com.animallocket.user.web;

import com.animallocket.common.Headers;
import com.animallocket.user.domain.FriendRequest;
import com.animallocket.user.dto.Dtos;
import com.animallocket.user.service.FriendService;
import com.animallocket.user.service.SafetyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Bạn bè, lời mời, chặn, báo cáo, thiết bị. */
@RestController
@RequestMapping("/api/v1")
public class SocialController {
    private final FriendService friends;
    private final SafetyService safety;

    public SocialController(FriendService friends, SafetyService safety) {
        this.friends = friends;
        this.safety = safety;
    }

    @GetMapping("/friends")
    public List<Dtos.PublicProfile> list(@RequestHeader(Headers.PUBLIC_ID) String me) {
        return friends.friends(me);
    }

    @DeleteMapping("/friends/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfriend(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String publicId) {
        friends.unfriend(me, publicId);
    }

    @GetMapping("/friends/requests/incoming")
    public List<Dtos.FriendRequestView> incoming(@RequestHeader(Headers.PUBLIC_ID) String me) {
        return friends.incoming(me);
    }

    @GetMapping("/friends/requests/outgoing")
    public List<Dtos.FriendRequestView> outgoing(@RequestHeader(Headers.PUBLIC_ID) String me) {
        return friends.outgoing(me);
    }

    @PostMapping("/friends/requests")
    public Dtos.ConnectResult invite(@RequestHeader(Headers.PUBLIC_ID) String me,
                                     @Valid @RequestBody Dtos.InviteRequest req) {
        return friends.connect(me, req.toPublicId(), FriendRequest.Source.INVITE);
    }

    @PostMapping("/friends/requests/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable long id) {
        friends.accept(me, id);
    }

    @PostMapping("/friends/requests/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable long id) {
        friends.decline(me, id);
    }

    @DeleteMapping("/friends/requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable long id) {
        friends.cancel(me, id);
    }

    @GetMapping("/blocks")
    public List<Dtos.PublicProfile> blocked(@RequestHeader(Headers.PUBLIC_ID) String me) {
        return safety.blocked(me);
    }

    @PutMapping("/blocks/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String publicId) {
        safety.block(me, publicId);
    }

    @DeleteMapping("/blocks/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String publicId) {
        safety.unblock(me, publicId);
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public void report(@RequestHeader(Headers.PUBLIC_ID) String me, @Valid @RequestBody Dtos.ReportRequest req) {
        safety.report(me, req);
    }

    @PutMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerDevice(@RequestHeader(Headers.PUBLIC_ID) String me,
                               @Valid @RequestBody Dtos.DeviceRequest req) {
        safety.registerDevice(me, req);
    }

    @DeleteMapping("/devices/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDevice(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String token) {
        safety.removeDevice(me, token);
    }
}
