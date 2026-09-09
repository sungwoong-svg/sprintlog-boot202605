package com.sprintlog.sprintlogboot.controller;

import com.sprintlog.sprintlogboot.dto.response.UserResponse;
import com.sprintlog.sprintlogboot.security.CustomUserDetails;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  @GetMapping("/whoami")
  public Map<String, Object> whoami() {
    // 1. SecurityContextHolder: 현재 스레드(=지금 이 요청)의 인증 정보 저장소에서 컨텍스트를 꺼낸다.
    SecurityContext context = SecurityContextHolder.getContext();

    // 2. SecurityContext: 그 안에 담긴 Authentication을 꺼낸다.
    Authentication authentication = context.getAuthentication();

    // 3. Authentication: 누가 무슨 권한, 인증됐는지를 표현
    // 4. getAuthorities() 안의 요소 하나하나가 GrantedAuthority - 권한 문자열로 변환
    List<String> authorities = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .toList();

    return Map.of(
        "name", authentication.getName(),
        "authorities", authorities,
        "authenticated", authentication.isAuthenticated(),
        "authenticationType", authentication.getClass().getSimpleName()
    );
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal CustomUserDetails principal) {
    return UserResponse.from(principal.getUser());
  }

}
