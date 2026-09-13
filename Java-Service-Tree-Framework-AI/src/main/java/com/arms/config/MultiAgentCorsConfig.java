package com.arms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * 프론트(Frontend-Web)와 백엔드(AI)를 별도 컨테이너·별도 오리진으로 배포할 때
 * 브라우저가 {@code /multiagent/**} 를 직접 호출할 수 있도록 허용한다.
 *
 * <p>허용 오리진은 배포 시 {@code MULTIAGENT_CORS_ALLOWED_ORIGIN} 환경변수로 좁힌다.
 * 기본값 {@code *} 는 MVP 단계 편의를 위한 것이며 운영 배포 시 반드시 실제 프론트 도메인으로
 * 좁혀야 한다.</p>
 */
@Configuration
public class MultiAgentCorsConfig implements WebFluxConfigurer {

    @Value("${multiagent.cors.allowed-origin:*}")
    private String allowedOrigin;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // /actuator/** 는 별도 디스패처(WebFluxEndpointHandlerMapping)를 쓰므로 여기서 적용되지 않는다.
        // 그쪽 CORS는 management.endpoints.web.cors.* 로 설정한다(application-multiagent-local.yml).
        registry.addMapping("/multiagent/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST")
                .allowedHeaders("*");
    }
}
