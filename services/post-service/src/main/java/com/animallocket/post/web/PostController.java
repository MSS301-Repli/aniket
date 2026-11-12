package com.animallocket.post.web;

import com.animallocket.common.Headers;
import com.animallocket.post.dto.PostDtos;
import com.animallocket.post.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PostController {
    private final PostService posts;

    public PostController(PostService posts) {
        this.posts = posts;
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostDtos.PostView create(@RequestHeader(Headers.PUBLIC_ID) String me,
                                    @Valid @RequestBody PostDtos.CreatePostRequest req) {
        return posts.create(me, req);
    }
}
