package com.sprintlog.sprintlogboot.dto.response;

public record TokenResponse(
    String accessToken,
    String tokenType,
    Long expiresIn,
    String refreshToken
) {

  @Deprecated
  public static TokenResponse bearer(String accessToken, Long expiresInSeconds) {
    return new TokenResponse(accessToken, "Bearer", expiresInSeconds, null);
  }
  public static TokenResponse bearer(String accessToken, Long expiresInSeconds, String refresh) {
    return new TokenResponse(accessToken, "Bearer", expiresInSeconds, refresh);
  }
}
