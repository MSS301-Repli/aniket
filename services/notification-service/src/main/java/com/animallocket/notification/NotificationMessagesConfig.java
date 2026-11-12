package com.animallocket.notification;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

@Configuration
public class NotificationMessagesConfig {
    /** Template thông báo nằm ở {@code i18n/notifications_<ngôn ngữ>.properties}, mã hoá UTF-8. */
    @Bean(name = "notificationMessages")
    MessageSource notificationMessages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/notifications");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
