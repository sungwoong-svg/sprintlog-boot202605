package com.sprintlog.sprintlogboot.dto.response;

public record TokenResponse(
    String accessToken,
    String tokenType,
    Long expiresIn
) {
  public static TokenResponse bearer(String accessToken, Long expiresInSeconds) {
    return new TokenResponse(accessToken, "Bearer", expiresInSeconds);
  }
}
