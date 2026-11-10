package com.animallocket.post.web;

import com.animallocket.common.Headers;
import com.animallocket.post.dto.PostDtos;
import com.animallocket.post.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

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

    @GetMapping("/posts/mine")
    public List<PostDtos.PostView> mine(@RequestHeader(Headers.PUBLIC_ID) String me,
                                        @RequestParam(defaultValue = "20") int limit) {
        return posts.mine(me, limit);
    }

    @GetMapping("/posts/{id}")
    public PostDtos.PostView get(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String id) {
        return posts.get(me, id);
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String id) {
        posts.delete(me, id);
    }

    /** Dưới /posts/** để gateway chuyển về post-service (/users/** thuộc user-service). */
    @GetMapping("/posts/by/{publicId}")
    public PostDtos.FeedPage userPosts(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String publicId,
                                       @RequestParam(required = false) Instant before,
                                       @RequestParam(defaultValue = "30") int limit) {
        return posts.userPosts(me, publicId, before, limit);
    }
}
