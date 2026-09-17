package com.example.clashroyaleapi.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageSourceConfig {

    // Bean名を messageSource にすると、Spring Boot の自動設定(spring.messages.*)の代わりにこちらが使われる。
    @Bean
    public MessageSource messageSource() {
        return IcuMessageSource.forBasename("messages");
    }
}
