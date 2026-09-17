package com.sprintlog.sprintlogboot.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("sprintlog.jwt")
public class JwtProperties {

  private String secret;

  private Duration accessTokenValidity = Duration.ofMinutes(30);

  private String issuer = "sprintlog";

}
