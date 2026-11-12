package com.animallocket.chat.config;

import org.springframework.context.annotation.Configuration;

/**
 * Base configuration for the chat service.
 *
 * <p>Feature-specific beans are added by the issues that own those features so
 * this initial module remains buildable without pulling later work forward.</p>
 */
@Configuration(proxyBeanMethods = false)
public class ChatConfig {
}
