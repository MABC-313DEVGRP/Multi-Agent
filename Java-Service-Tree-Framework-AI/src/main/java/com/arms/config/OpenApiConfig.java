package com.arms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components())
                .info(apiInfo());
    }

    private Info apiInfo() {
        return new Info()
                .title("ARMS API")
                .description("ARMS API");
    }

    @Bean
    public GroupedOpenApi armsApi() {
        return GroupedOpenApi.builder()
                .group("arms")
                .packagesToScan("com.arms") // API를 스캔할 패키지
                .build();
    }




}