package com.sprintlog.sprintlogboot.security;

import com.sprintlog.sprintlogboot.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  /** EntryPoint 에 실패 사유를 전달하는 request attribute 키. */
  public static final String ATTR_JWT_ERROR = "jwt.error";
  public static final String ERROR_EXPIRED = "expired";
  public static final String ERROR_INVALID = "invalid";

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtProvider jwtProvider;
  private final UserDetailsService userDetailsService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    String token = resolveToken(request);

    if (token != null) {
      try {
        Claims claims = jwtProvider.parseClaims(token);
        String username = claims.getSubject();
        Role role = jwtProvider.getRole(claims);

        // 사용자 로드 -> DB로 실존 확인
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // Security Context에 '인증 완료' 상태의 Authentication을 채운다.
        // 이걸 채워 놓아야 이후의 인가 (@PreAuthorize, AuthorizationFilter)는
        // 인증이 어디서 왔는지(세션인지 토큰인지) 모른채 똑같이 동작한다.
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
            userDetails, null, userDetails.getAuthorities()
        );
        // 들어오는 HTTP 요청으로부터 인증과 관련된 부가적인 웹 메타데이터를 추출해서 인증 정보에 세팅하는 로직
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // 빈 컨텍스트를 만들어서 인증 정보를 저장한다.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        log.debug("[JWT] 인증 성공 - user={}, role={}", username, role);

        // 에러의 원인을 request 객체에 담아놓음 -> EntryPoint가 request 객체를 받아서 응답에 직접 활용 예정 
      } catch (ExpiredJwtException e) {
        request.setAttribute(ATTR_JWT_ERROR, ERROR_EXPIRED);
        log.debug("[JWT] 만료된 토큰으로 접근 - {}", e.getMessage());
      } catch (JwtException | IllegalArgumentException e) {
        request.setAttribute(ATTR_JWT_ERROR, ERROR_INVALID);
        log.debug("[JWT] 유효하지 않은 토큰으로 접근 - {}", e.getMessage());
      } catch (UsernameNotFoundException e) {
        request.setAttribute(ATTR_JWT_ERROR, ERROR_INVALID);
        log.debug("[JWT] 토큰의 사용자가 존재하지 않음 - {}", e.getMessage());
      }
    }

    // 토큰이 있든 없든, 검증에 실패했든 필터 체인은 계속 진행되어야 한다.
    // 요청이 공개 경로 요청인 경우에는 익명으로 그냥 통과 (회원가입, 로그인, 게시글 조회 등등)

    filterChain.doFilter(request, response);

  }

  // Authorization: Bearer <토큰> -> 헤더에서 토큰만 꺼내서 리턴 (형식이 어긋나면 null)
  private String resolveToken(HttpServletRequest request) {

    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    return null;
  }
}
