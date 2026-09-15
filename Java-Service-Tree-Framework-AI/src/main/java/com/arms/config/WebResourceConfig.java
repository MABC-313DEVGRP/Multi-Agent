package com.arms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.resource.PathResourceResolver;
import reactor.core.publisher.Mono;

@Configuration
public class WebResourceConfig implements WebFluxConfigurer {

    private static final String STATIC_LOCATION = "classpath:/static/";
    private static final String INDEX = "index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_LOCATION)
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    @Bean
    public RouterFunction<ServerResponse> spaIndexRoute() {
        Resource index = new ClassPathResource("static/" + INDEX);
        return RouterFunctions.route(
                RequestPredicates.GET("/"),
                request -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .bodyValue(index));
    }

    static class SpaResourceResolver extends PathResourceResolver {

        @Override
        protected Mono<Resource> getResource(String resourcePath, Resource location) {
            String path = (resourcePath == null || resourcePath.isBlank()) ? INDEX : resourcePath;
            return super.getResource(path, location)
                    .switchIfEmpty(Mono.defer(() -> {
                        if (isExcludedFromSpaFallback(path)) {
                            return Mono.empty();
                        }
                        return super.getResource(INDEX, location);
                    }));
        }

        /** SPA 폴백을 적용하지 않을 경로(실제 애셋 요청이거나 다른 핸들러 담당) */
        private boolean isExcludedFromSpaFallback(String path) {
            // 확장자가 있으면 실제 파일 요청 → 404 를 그대로 둔다(폴백 금지)
            if (path.contains(".")) {
                return true;
            }
            // resourcePath 는 앞의 '/' 가 제거된 상대 경로로 전달된다
            return path.startsWith("multiagent")
                    || path.startsWith("actuator")
                    || path.startsWith("swagger-ui")
                    || path.startsWith("v3/")
                    || path.startsWith("webjars");
        }
    }
}
