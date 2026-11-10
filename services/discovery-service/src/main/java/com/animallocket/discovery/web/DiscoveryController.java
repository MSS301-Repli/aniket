package com.animallocket.discovery.web;

import com.animallocket.common.Headers;
import com.animallocket.discovery.service.DiscoveryDtos;
import com.animallocket.discovery.service.DiscoveryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(DiscoveryController.class);
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

    /** Fail soft: lỗi hạ tầng thì trả danh sách rỗng chứ không làm hỏng app. Lỗi nghiệp vụ (429, 403) vẫn trả đúng mã. */
    @GetMapping("/cards")
    public DiscoveryDtos.CardsResponse cards(@RequestHeader(Headers.PUBLIC_ID) String me,
                                             @RequestParam(defaultValue = "20") int limit) {
        try {
            return discovery.cards(me, limit);
        } catch (com.animallocket.common.web.ApiException e) {
            if (e.status() == HttpStatus.SERVICE_UNAVAILABLE) {
                log.warn("Bộ thẻ tạm thời không có: {}", e.getMessage());
                return new DiscoveryDtos.CardsResponse(List.of(), true, 0);
            }
            throw e;
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("Redis lỗi, trả bộ thẻ rỗng: {}", e.toString());
            return new DiscoveryDtos.CardsResponse(List.of(), true, 0);
        }
    }

    @PostMapping("/swipes")
    public DiscoveryDtos.SwipeResponse swipe(@RequestHeader(Headers.PUBLIC_ID) String me,
                                             @Valid @RequestBody DiscoveryDtos.SwipeRequest req) {
        return discovery.swipe(me, req.targetPublicId(), req.direction());
    }

    /** Header Idempotency-Key (tuỳ chọn): gửi lại cùng khoá thì không bị trừ Boost lần nữa. */
    @PostMapping("/boost")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void boost(@RequestHeader(Headers.PUBLIC_ID) String me,
                      @RequestHeader(value = Headers.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        discovery.boost(me, idempotencyKey);
    }
}
