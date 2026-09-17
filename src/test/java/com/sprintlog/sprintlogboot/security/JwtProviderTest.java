package com.sprintlog.sprintlogboot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintlog.sprintlogboot.config.JwtProperties;
import com.sprintlog.sprintlogboot.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JwtProvider (JWT 생성, 검증, 클레임 추출)")
class JwtProviderTest {

  /** 지난 시간 디버거 실습에서 쓴 그 키 - 33바이트라 HS256 최소 요건(32바이트)을 넘는다. */
  private static final String SECRET = "sprintlog-jwt-secret-key-32bytes!";
  private static final String ISSUER = "sprintlog";
  private static final Duration VALIDITY = Duration.ofMinutes(30);

  /** 테스트 기준 시각 - JWT 의 시각(NumericDate)은 초 단위라 정확히 0초로 고정한다. */
  private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");

  private JwtProvider provider = providerOf(SECRET, ISSUER, VALIDITY, NOW);

  /** 키·발급자·유효시간·현재시각을 바꿔 가며 JwtProvider 를 만드는 헬퍼. */
  private static JwtProvider providerOf(String secret, String issuer, Duration validity, Instant now) {
    JwtProperties props = new JwtProperties();
    props.setSecret(secret);
    props.setIssuer(issuer);
    props.setAccessTokenValidity(validity);
    return new JwtProvider(props, Clock.fixed(now, ZoneId.of("UTC")));
  }

  @Nested
  @DisplayName("토큰 생성")
  class CreateToken {

    @Test
    @DisplayName("점(.)으로 구분된 세 조각(헤더.페이로드.서명)이 나온다")
    void 세_조각_구조() {
      String token = provider.createAccessToken("alice", Role.USER);

      assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("헤더에는 alg=HS256, typ=JWT 가 들어간다")
    void 헤더_알고리즘() {
      String token = provider.createAccessToken("alice", Role.USER);

      String headerJson = new String(
          Base64.getUrlDecoder().decode(token.split("\\.")[0]),
          StandardCharsets.UTF_8);

      assertThat(headerJson).contains("\"alg\":\"HS256\"");
    }

    @Test
    @DisplayName("만료 시각 = 발급 시각 + 설정한 유효 시간")
    void 만료_시각() {
      String token = provider.createAccessToken("alice", Role.USER);

      Claims claims = provider.parseClaims(token);

      assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
      assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plus(VALIDITY));
    }
  }

  @Nested
  @DisplayName("클레임 추출")
  class ExtractClaims {

    @Test
    @DisplayName("넣은 사용자 이름·역할·발급자를 그대로 돌려받는다")
    void 클레임_라운드트립() {
      String token = provider.createAccessToken("alice", Role.ADMIN);

      assertThat(provider.getUsername(token)).isEqualTo("alice");
      assertThat(provider.getRole(token)).isEqualTo(Role.ADMIN);
      assertThat(provider.parseClaims(token).getIssuer()).isEqualTo(ISSUER);
    }
  }

  @Nested
  @DisplayName("토큰 검증")
  class ValidateToken {

    @Test
    @DisplayName("방금 만든 정상 토큰은 유효하다")
    void 정상_토큰() {
      String token = provider.createAccessToken("alice", Role.USER);

      assertThat(provider.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("만료된 토큰은 ExpiredJwtException - isValid 는 false")
    void 만료된_토큰() {
      // 기준 시각의 2시간 전에 발급 + 유효 30분 → 기준 시각(NOW)의 provider 가 보기엔 이미 만료
      JwtProvider pastProvider = providerOf(
          SECRET, ISSUER, VALIDITY, NOW.minus(Duration.ofHours(2)));

      String expired = pastProvider.createAccessToken("alice", Role.USER);

      assertThatThrownBy(() -> provider.parseClaims(expired))
          .isInstanceOf(ExpiredJwtException.class);
      assertThat(provider.isValid(expired)).isFalse();
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 SignatureException - 위조 감지")
    void 다른_키_서명() {
      JwtProvider attacker = providerOf(
          "attacker-made-this-secret-key-32b!", ISSUER, VALIDITY, NOW);

      String forged = attacker.createAccessToken("alice", Role.ADMIN);

      assertThatThrownBy(() -> provider.parseClaims(forged))
          .isInstanceOf(SignatureException.class);
      assertThat(provider.isValid(forged)).isFalse();
    }

    @Test
    @DisplayName("페이로드를 바꿔치기하면 서명 검증에 실패한다 - 변조 감지")
    void 페이로드_변조() {
      // 지난 시간 F12 콘솔에서 했던 role 바꿔치기를 코드로 재현한다
      String token = provider.createAccessToken("alice", Role.USER);
      String[] parts = token.split("\\.");

      String payloadJson = new String(
          Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
      String tamperedJson = payloadJson.replace("\"USER\"", "\"ADMIN\"");
      String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
          .encodeToString(tamperedJson.getBytes(StandardCharsets.UTF_8));

      String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

      assertThatThrownBy(() -> provider.parseClaims(tampered))
          .isInstanceOf(SignatureException.class);
      assertThat(provider.isValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("서명이 없는 토큰(alg:none)은 거부된다")
    void 무서명_토큰_거부() {
      // 헤더의 alg 를 none 으로 선언하고 서명 조각을 비운 공격 토큰
      Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
      String header = encoder.encodeToString(
          "{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
      String payload = encoder.encodeToString(
          ("{\"sub\":\"alice\",\"role\":\"ADMIN\",\"iss\":\"" + ISSUER + "\"}")
              .getBytes(StandardCharsets.UTF_8));

      String unsigned = header + "." + payload + ".";

      // parseSignedClaims 는 '서명된 JWS' 만 받는다 - 무서명 토큰은 그 정책에 걸려 거부(UnsupportedJwtException 계열)
      assertThatThrownBy(() -> provider.parseClaims(unsigned))
          .isInstanceOf(JwtException.class);
      assertThat(provider.isValid(unsigned)).isFalse();
    }

    @Test
    @DisplayName("발급자(iss)가 다른 토큰은 거부된다 - 같은 키를 쓰는 다른 서비스의 토큰 방어")
    void 발급자_불일치() {
      JwtProvider otherService = providerOf(SECRET, "other-service", VALIDITY, NOW);

      String otherToken = otherService.createAccessToken("alice", Role.USER);

      assertThatThrownBy(() -> provider.parseClaims(otherToken))
          .isInstanceOf(IncorrectClaimException.class);
      assertThat(provider.isValid(otherToken)).isFalse();
    }
  }

  @Nested
  @DisplayName("키 강도 검사")
  class KeyStrength {

    @Test
    @DisplayName("32바이트 미만 키는 WeakKeyException - 기동 자체가 실패한다(fail-fast)")
    void 약한_키_거부() {
      assertThatThrownBy(() -> providerOf("too-short-key", ISSUER, VALIDITY, NOW))
          .isInstanceOf(WeakKeyException.class);
    }
  }

}