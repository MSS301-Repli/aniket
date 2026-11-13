package com.animallocket.common.web;

import com.animallocket.common.Headers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Bảo vệ {@code /internal/**}: chỉ service khác giữ khoá chung (header {@code X-Internal-Token}) mới gọi được.
 * So sánh bằng {@link MessageDigest#isEqual} (thời gian hằng) để không lộ khoá qua thời gian phản hồi.
 * Gateway cũng chặn {@code /internal/**} từ bên ngoài; filter này là lớp bảo vệ thứ hai.
 */
public class InternalTokenFilter extends OncePerRequestFilter {
    static final String INTERNAL_PREFIX = "/internal/";

    private final byte[] expected;

    public InternalTokenFilter(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Internal token không được rỗng");
        }
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Dùng servletPath + pathInfo (đã chuẩn hoá, bỏ context path) thay vì requestURI thô
        // để "/x/../internal/" hay "//internal/" không lách được.
        String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        if (path.isEmpty()) {
            path = request.getRequestURI();
        }
        return !(path.startsWith(INTERNAL_PREFIX) || path.equals("/internal"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String given = req.getHeader(Headers.INTERNAL_TOKEN);
        boolean ok = given != null && MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), expected);
        if (!ok) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
            res.setContentType("application/problem+json");
            res.getWriter().write("{\"title\":\"Forbidden\",\"status\":403,\"code\":\"INTERNAL_ONLY\","
                    + "\"detail\":\"Chỉ dùng nội bộ\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
