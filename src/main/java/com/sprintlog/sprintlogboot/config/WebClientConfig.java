package com.sprintlog.sprintlogboot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

  @Bean
  public WebClient slowApiClient(WebClient.Builder builder,
      TraceIdForwardingFilter traceIdForwarding,
      @Value("${sprintlog.slow-api.url:http://localhost:8081}") String baseUrl) {
    return builder
        .baseUrl(baseUrl)
        .defaultHeader("X-Called-by", "sprintlog")
        .filter(traceIdForwarding)
        .build();

  }

}
