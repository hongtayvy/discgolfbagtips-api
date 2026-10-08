package com.discgolfbagtips.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Referenced by {@code @SecurityRequirement} on the endpoints that need a signed-in user. */
    public static final String SUPABASE_BEARER = "supabase";

    /**
     * Set on a deployed instance so Swagger's "Try it out" targets the public host. Left blank
     * locally, where springdoc's relative default is already correct — declaring an empty server
     * would break the button rather than improve it, which is why this is conditional here instead
     * of a property with an empty default.
     */
    @Value("${bagtips.public-base-url:}")
    private String publicBaseUrl;

    @Bean
    public OpenAPI bagTipsOpenApi() {
        OpenAPI openApi = new OpenAPI().info(new Info()
                .title("Disc Golf Bag Tips API")
                .version("v1")
                .description("""
                        Retrieval-augmented disc golf bag analysis. A request carries the player's current \
                        bag (mold + plastic), a lightweight profile, and course/weather conditions; the \
                        response carries a recommended next disc plus an explainability payload naming the \
                        vector matches and flight-number deltas that justified it.

                        Disc catalog data is synced from the open-source DiscIt API.
                        """)
                .contact(new Contact().name("Victor Yang").url("https://github.com/hongtayvy"))
                .license(new License().name("MIT").url("https://opensource.org/license/mit")))
                // Optional everywhere except /account; a Supabase access token, pasted into "Authorize".
                .components(new Components().addSecuritySchemes(SUPABASE_BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Supabase Auth access token")));

        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            openApi.setServers(List.of(new Server().url(publicBaseUrl).description("Deployed")));
        }
        return openApi;
    }
}
