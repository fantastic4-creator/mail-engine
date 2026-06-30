package com.mailengine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailengine.service.ApiKeyService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    public WebMvcConfig(ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiKeyAuthInterceptor(apiKeyService, objectMapper))
                .excludePathPatterns(
                        "/actuator/**",
                        "/unsubscribe",
                        "/webhooks/**",
                        "/api/tenants",           // create first tenant
                        "/api/tenants/*/api-keys" // create first API key per tenant
                );
    }
}
