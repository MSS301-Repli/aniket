package com.animallocket.discovery.web;

import com.animallocket.common.Headers;
import com.animallocket.discovery.service.DiscoveryDtos;
import com.animallocket.discovery.service.DiscoveryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/api/v1/discovery")
public class DiscoveryController {
    private final DiscoveryService discovery;

    public DiscoveryController(DiscoveryService discovery) {
        this.discovery = discovery;
    }

    @PutMapping("/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void location(@RequestHeader(Headers.PUBLIC_ID) String me, @Valid @RequestBody DiscoveryDtos.LocationRequest req) {
        discovery.updateLocation(me, req.lat(), req.lon());
    }

    @DeleteMapping("/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearLocation(@RequestHeader(Headers.PUBLIC_ID) String me) {
        discovery.clearLocation(me);
    }

    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(@RequestHeader(Headers.PUBLIC_ID) String me) {
        discovery.heartbeat(me);
    }

    @GetMapping("/presence")
    public DiscoveryDtos.PresenceResponse presence(@RequestHeader(Headers.PUBLIC_ID) String me,
                                                   @RequestParam List<String> ids) {
        return discovery.presence(me, new HashSet<>(ids.stream().limit(200).toList()));
    }
}
