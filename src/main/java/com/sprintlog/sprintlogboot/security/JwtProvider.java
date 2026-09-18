package com.sprintlog.sprintlogboot.security;

import com.sprintlog.sprintlogboot.config.JwtProperties;
import com.sprintlog.sprintlogboot.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/*
이 클래스가 JWT의 전부를 담당한다. (생성/검증/추출)
 */
@Component
@Slf4j
public class JwtProvider {

  // 이메일은 변경 가능성이 열려 있으므로 변하지 않는 내부 식별자를 따로 싣는다. (PK)
  public static final String CLAIM_USER_ID = "uid";

  public static final String CLAIM_ROLE = "role";
  private final JwtProperties properties;
  private final Clock clock;
  private final SecretKey secretKey;

  public JwtProvider(JwtProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    this.secretKey = Keys.hmacShaKeyFor(
        properties.getSecret().getBytes(StandardCharsets.UTF_8)
    );
  }

  // Access Token 생성
  // 페이로드 : sub(사용자 이름), role(역할), iss(발급자), iat(발급 시간), exp(만료 시간)
  public String createAccessToken(Long userId, String username, Role role) {
    Instant now = clock.instant();
    Instant expiry = now.plus(properties.getAccessTokenValidity());

    return Jwts.builder()
        .subject(username)
        .claims(Map.of(CLAIM_ROLE, role.name(), CLAIM_USER_ID, userId))
        .issuer(properties.getIssuer())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        // 알고리즘을 HS256으로 명시 고정 - 토큰 헤더의 alg를 믿지 않고 서버가 정한다.
        .signWith(secretKey, Jwts.SIG.HS256)
        .compact();
  }

  // 토큰을 검증하고 페이로드(Claims)를 돌려준다.
  public Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(secretKey)
        .requireIssuer(properties.getIssuer())
        // 만료(exp) 판정도 주입 받은 시계 기준 - 발급과 검증이 같은 시계를 쓴다.(테스트에서 시간 제어 가능)
        .clock(() -> Date.from(clock.instant()))
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  /**
   * 유효 여부만 빠르게 판단하는 편의 메서드 (예외 → false).
   * 어떤 이유로 거부됐는지는 로그로 남긴다.
   */
  public boolean isValid(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (ExpiredJwtException e) {
      log.warn("[JWT] 만료된 토큰: {}", e.getMessage());
    } catch (JwtException | IllegalArgumentException e) {
      log.warn("[JWT] 유효하지 않은 토큰: {}", e.getMessage());
    }
    return false;
  }

  /** 검증을 통과한 토큰에서 사용자 이름(sub) 추출. */
  public String getUsername(String token) {
    return parseClaims(token).getSubject();
  }

  /** 검증을 통과한 토큰에서 역할(role 클레임) 추출. */
  public Role getRole(String token) {
    return getRole(parseClaims(token));
  }

  // 이미 검증된 Claims에서 역할 추출
  public Role getRole(Claims claims) {
    return Role.valueOf(claims.get(CLAIM_ROLE, String.class));
  }

  // 토큰의 유효시간을 초로 리턴
  public Long getAccessTokenValiditySeconds() {
    return properties.getAccessTokenValidity().toSeconds();
  }

  public Long getUserId(Claims claims) {
    Number uid = claims.get(CLAIM_USER_ID, Number.class);
    return uid == null ? null : uid.longValue();
  }


}
