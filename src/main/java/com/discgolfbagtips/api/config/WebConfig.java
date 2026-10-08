package com.discgolfbagtips.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the separately deployed front end. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final BagTipsProperties properties;

    public WebConfig(BagTipsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(properties.cors().allowedOrigins().toArray(String[]::new))
                // DELETE for saved bags. Without it a cross-origin front end fails the preflight and
                // the delete never reaches the API — invisible locally, where Vite proxies same-origin.
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
