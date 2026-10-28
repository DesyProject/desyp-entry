package com.desyp.entry.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig implements WebMvcConfigurer {

    private final EventProperties properties;

    public AppConfig(EventProperties properties) {
        this.properties = properties;
    }

    /** 응모 시간 판단의 기준. 테스트에서 고정 시계로 바꾼다 */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** 로그인·쿠키가 없으므로 credentials는 허용하지 않는다 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/event/**")
                .allowedOrigins(properties.allowedOrigin())
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "X-Client-Id")
                .allowCredentials(false)
                .maxAge(600);
    }
}
