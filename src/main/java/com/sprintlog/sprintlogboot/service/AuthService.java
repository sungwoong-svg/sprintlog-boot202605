package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.dto.request.LoginRequest;
import com.sprintlog.sprintlogboot.dto.response.TokenResponse;
import com.sprintlog.sprintlogboot.security.CustomUserDetails;
import com.sprintlog.sprintlogboot.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

  private final AuthenticationManager authenticationManager;
  private final JwtProvider jwtProvider;

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

    return TokenResponse.bearer(token, jwtProvider.getAccessTokenValiditySeconds());

  }

}
