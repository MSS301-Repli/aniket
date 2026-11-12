package com.animallocket.media.web;

import com.animallocket.common.Headers;
import com.animallocket.media.dto.MediaDtos;
import com.animallocket.media.service.MediaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MediaController {
    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    @PostMapping("/api/v1/media/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaDtos.UploadTicket upload(@RequestHeader(Headers.PUBLIC_ID) String me,
                                         @Valid @RequestBody MediaDtos.UploadRequest req) {
        return media.requestUpload(me, req);
    }

    @PostMapping("/api/v1/media/{id}/complete")
    public MediaDtos.MediaStatus complete(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String id) {
        return media.complete(me, id);
    }

    @GetMapping("/api/v1/media/{id}")
    public MediaDtos.MediaStatus status(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String id) {
        return media.status(me, id);
    }

    @GetMapping("/api/v1/media/{id}/url")
    public MediaDtos.ViewUrl url(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String id) {
        return media.viewUrl(me, id);
    }

    @GetMapping("/internal/media/{id}")
    public MediaDtos.InternalMedia internal(@PathVariable String id) {
        return media.internal(id);
    }
}
