package com.animallocket.chat.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ChatConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ChatConfig.class);

    @Test
    void registersBaseConfigurationWithoutFeatureBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChatConfig.class);
            assertThat(context).hasNotFailed();
        });
    }
}
