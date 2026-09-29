package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.dto.request.LoginRequest;
import com.sprintlog.sprintlogboot.dto.response.TokenResponse;
import com.sprintlog.sprintlogboot.security.CustomUserDetails;
import com.sprintlog.sprintlogboot.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

  private final AuthenticationManager authenticationManager;
  private final JwtProvider jwtProvider;
  private final RefreshTokenService refreshTokenService;

  public TokenResponse login(LoginRequest request) {

    // 1. 검증 전 인증 표를 만든다.(아직 인증 전)
    Authentication unauthenticated
        = UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password());

    // 2. 매니저에게 검증을 맡긴다. 실패하면 여기서 예외가 발생한다.
    Authentication authenticated = authenticationManager.authenticate(unauthenticated);

    // 3. 성공 - CustomUserDetails를 받아서 토큰을 발급한다.
    CustomUserDetails principal = (CustomUserDetails) authenticated.getPrincipal();

    log.info("[AUTH] 로그인 성공 - user={}, role={}", principal.getUsername(), principal.getUser().getRole());

    String token = jwtProvider.createAccessToken(
        principal.getUser().getId(),
        principal.getUsername(),
        principal.getUser().getRole()
    );

    // Access와 함께 Refresh도 발급한다.
    String refresh = refreshTokenService.issue(principal.getUser());

    return TokenResponse.bearer(token, jwtProvider.getAccessTokenValiditySeconds(), refresh);
  }

  // Access Token 토큰 만료 시 새 Access Token 발급 로직, Refresh Token도 rotate
  public TokenResponse refresh(String rawRefreshToken) {
    RefreshTokenService.Outcome outcome = refreshTokenService.rotate(rawRefreshToken);

    // 거부는 여기서 — rotate 안에서 던지면 재사용 감지의 무효화까지 롤백된다
    switch (outcome.status()) {
      case NOT_FOUND -> throw new BadCredentialsException("유효하지 않은 Refresh Token 입니다.");
      case EXPIRED   -> throw new BadCredentialsException("Refresh Token 이 만료되었습니다. 다시 로그인해 주세요.");
      case REUSE_DETECTED -> throw new BadCredentialsException(
          "보안을 위해 모든 세션이 종료되었습니다. 다시 로그인해 주세요.");
      case SUCCESS -> { /* 아래로 진행 */ }
    }

    String access = jwtProvider.createAccessToken(
        outcome.user().getId(), outcome.user().getEmail(), outcome.user().getRole());
    log.info("[AUTH] 토큰 재발급 - user={}", outcome.user().getEmail());
    return TokenResponse.bearer(access, jwtProvider.getAccessTokenValiditySeconds(), outcome.refreshToken());

  }

  // 로그아웃 - 지금 쓰는 리프레시 하나만 끊어준다.
  public void logout(String rawRefreshToken) {
    refreshTokenService.revokeOne(rawRefreshToken);
  }

}
