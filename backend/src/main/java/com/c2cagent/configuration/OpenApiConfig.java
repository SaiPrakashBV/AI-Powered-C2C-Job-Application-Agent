package com.c2cagent.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI c2cAgentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("C2C Job Application Agent API")
                .version("v1")
                .description("Local REST API. All /api/v1 endpoints except /api/v1/auth/* require an authenticated "
                        + "session; state-changing requests require the X-XSRF-TOKEN header."));
    }
}
