package com.animallocket.user.service;

import com.animallocket.user.repo.AvatarPoolRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nạp mọi tổ hợp của thư viện vào kho (idempotent: chỉ chèn khoá còn thiếu, không đụng dòng đã có chủ).
 * Phát hành thư viện mới = thêm file avatar-lib.vN.json, lần khởi động sau kho tự lớn lên.
 */
@Component
@Order(10)
public class AvatarPoolSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AvatarPoolSeeder.class);

    private static final String INSERT = "INSERT INTO avatar_pool (avatar_key, lib_version) VALUES (?, ?)";

    private final AvatarLibrary library;
    private final AvatarPoolRepository pool;
    private final JdbcTemplate jdbc;

    public AvatarPoolSeeder(AvatarLibrary library, AvatarPoolRepository pool, JdbcTemplate jdbc,
                            ObjectProvider<MeterRegistry> meters) {
        this.library = library;
        this.pool = pool;
        this.jdbc = jdbc;
        meters.ifAvailable(m -> Gauge.builder("avatar_pool_free", pool, AvatarPoolRepository::countByOwnerPublicIdIsNull)
                .description("Số avatar còn trống trong kho; về 0 thì không đăng ký được nữa")
                .register(m));
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> existing = new HashSet<>(pool.allKeys());
        List<Object[]> missing = library.allKeys().entrySet().stream()
                .filter(e -> !existing.contains(e.getKey()))
                .map(e -> new Object[]{e.getKey(), e.getValue()})
                .toList();
        if (!missing.isEmpty()) {
            try {
                jdbc.batchUpdate(INSERT, missing);
            } catch (DataIntegrityViolationException race) {
                // Instance khác vừa seed cùng lúc: chèn từng dòng, bỏ qua dòng đã có.
                for (Object[] row : missing) {
                    try {
                        jdbc.update(INSERT, row);
                    } catch (DataIntegrityViolationException alreadyThere) {
                        // đã có
                    }
                }
            }
        }
        log.info("Kho avatar: thêm {} tổ hợp, tổng {}, còn trống {}", missing.size(),
                existing.size() + missing.size(), pool.countByOwnerPublicIdIsNull());
    }
}
