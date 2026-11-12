package com.animallocket.common.config;

import com.animallocket.common.web.ApiExceptionHandler;
import com.animallocket.common.web.InternalTokenFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Cấu hình chung cho mọi service servlet: xử lý lỗi RFC 7807 và bảo vệ {@code /internal/**}.
 * Khoá nội bộ lấy từ {@code animal.internal-token}; ở profile {@code prod} thiếu/yếu khoá thì dừng khởi động
 * (xem {@link InternalTokens}).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(ApiExceptionHandler.class)
public class CommonAutoConfiguration {

    @Bean
    InternalTokenFilter internalTokenFilter(Environment env) {
        boolean prod = env.acceptsProfiles(Profiles.of("prod"));
        return new InternalTokenFilter(InternalTokens.resolve(env.getProperty(InternalTokens.PROPERTY), prod));
    }
}
