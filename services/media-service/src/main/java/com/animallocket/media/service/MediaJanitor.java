package com.animallocket.media.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "animal.media.janitor.enabled", havingValue = "true", matchIfMissing = true)
public class MediaJanitor {
    private static final Logger log = LoggerFactory.getLogger(MediaJanitor.class);

    private final MediaService media;
    public MediaJanitor(MediaService media) {
        this.media = media;
    }

    @Scheduled(fixedDelayString = "${animal.media.janitor.interval-ms:60000}")
    public void expireAbandonedJobs() {
        try {
            int n = media.expireAbandoned();
            if (n > 0) {
                log.info("Xoá {} yêu cầu upload bị bỏ dở", n);
            }
        } catch (Exception e) {
            log.error("expireAbandonedJobs lỗi", e);
        }
    }
}
