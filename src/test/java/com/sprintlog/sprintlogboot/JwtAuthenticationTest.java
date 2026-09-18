package com.sprintlog.sprintlogboot;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintlog.sprintlogboot.config.JwtProperties;
import com.sprintlog.sprintlogboot.domain.Role;
import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import com.sprintlog.sprintlogboot.security.JwtProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("JWT 인증 통합 테스트 (Bearer 토큰 + 필터 + STATELESS)")
public class JwtAuthenticationTest {

  @LocalServerPort
  int port;
  @Autowired
  TestRestTemplate rest;
  @Autowired
  UserRepository userRepository;
  @Autowired
  PasswordEncoder passwordEncoder;
  @Autowired
  JwtProvider jwtProvider;         // 앱이 쓰는 그 공장 — 유효 토큰 발급용
  @Autowired
  JwtProperties jwtProperties;     // 만료·위조 토큰 제작에 '같은 설정' 을 재료로 쓴다

  String base;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    userRepository.save(new User("김춘식", "choon@naver.com", passwordEncoder.encode("password123")));                 // ROLE_USER
    userRepository.save(new User("관리자", "admin@sprintlog.com", passwordEncoder.encode("admin123"), Role.ADMIN));    // ROLE_ADMIN
    base = "http://localhost:" + port;
  }

  /** Authorization: Bearer 헤더를 단 (본문 없는) 요청 엔티티. */
  private HttpEntity<Void> bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return new HttpEntity<>(headers);
  }

  /** GET 요청 + Bearer 토큰 → 응답. */
  private ResponseEntity<String> getWithToken(String url, String token) {
    return rest.exchange(base + url, HttpMethod.GET, bearer(token), String.class);
  }

  // ── 인증 성공 ──────────────────────────────────────────────

  @Test
  @DisplayName("유효한 토큰 → 보호 API(/me) 200, 토큰의 사용자로 인증된다")
  void 유효_토큰_인증() {
    String token = jwtProvider.createAccessToken("choon@naver.com", Role.USER);

    ResponseEntity<String> res = getWithToken("/api/v1/auth/me", token);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).contains("choon@naver.com");
  }

  @Test
  @DisplayName("whoami — 필터가 채운 SecurityContext: 실제 사용자 + ROLE_USER + 인증 완료 상태")
  void 필터가_채운_인증_상태() {
    String token = jwtProvider.createAccessToken("choon@naver.com", Role.USER);

    ResponseEntity<String> res = getWithToken("/api/v1/auth/whoami", token);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).contains("choon@naver.com");                     // 익명이 아니다
    assertThat(res.getBody()).contains("ROLE_USER");
    assertThat(res.getBody()).contains("UsernamePasswordAuthenticationToken"); // 필터가 만든 '인증 완료' 토큰
  }

  @Test
  @DisplayName("STATELESS 증명 — 인증 요청에도 세션 쿠키(JSESSIONID)가 발급되지 않는다")
  void 세션_쿠키_미발급() {
    String token = jwtProvider.createAccessToken("choon@naver.com", Role.USER);

    ResponseEntity<String> res = getWithToken("/api/v1/auth/me", token);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    // 세션 세계에서는 인증하면 JSESSIONID Set-Cookie 가 왔다 — 이제 그 반대를 증명한다.
    List<String> setCookies = res.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(setCookies == null
        || setCookies.stream().noneMatch(c -> c.contains("JSESSIONID"))).isTrue();
  }

  // ── 인증 실패 3종 — EntryPoint 가 사유를 구분해 응답한다 ──────

  @Test
  @DisplayName("토큰 없음 → 401 + AUTH_401 (로그인 필요)")
  void 무토큰_401() {
    ResponseEntity<String> res = rest.getForEntity(base + "/api/v1/auth/me", String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    // ProblemDetail 표준 응답(application/problem+json — charset 파라미터가 붙을 수 있어 호환 비교)
    assertThat(res.getHeaders().getContentType()
        .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
    assertThat(res.getBody()).contains("AUTH_401").doesNotContain("EXPIRED").doesNotContain("INVALID");
  }

  @Test
  @DisplayName("만료된 토큰 → 401 + AUTH_401_EXPIRED (재로그인 신호)")
  void 만료_토큰_401_EXPIRED() {
    // 앱과 같은 설정(같은 키·발급자)에 '2시간 전에 멈춘 시계' 만 끼운 공장 → 이미 만료된 진짜 서명 토큰
    JwtProvider pastFactory = new JwtProvider(jwtProperties,
        Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneId.of("UTC")));
    String expired = pastFactory.createAccessToken("choon@naver.com", Role.USER);

    ResponseEntity<String> res = getWithToken("/api/v1/auth/me", expired);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(res.getBody()).contains("AUTH_401_EXPIRED");
  }

  @Test
  @DisplayName("다른 키로 서명한(위조) 토큰 → 401 + AUTH_401_INVALID")
  void 위조_토큰_401_INVALID() {
    // 공격자의 공장 — 키만 다르고 나머지 클레임은 완벽한 토큰
    JwtProperties attackerProps = new JwtProperties();
    attackerProps.setSecret("attacker-made-this-secret-key-32b!");
    attackerProps.setIssuer(jwtProperties.getIssuer());
    attackerProps.setAccessTokenValidity(jwtProperties.getAccessTokenValidity());
    JwtProvider attackerFactory = new JwtProvider(attackerProps, Clock.systemUTC());
    String forged = attackerFactory.createAccessToken("choon@naver.com", Role.ADMIN);

    ResponseEntity<String> res = getWithToken("/api/v1/auth/me", forged);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(res.getBody()).contains("AUTH_401_INVALID");
  }

  @Test
  @DisplayName("토큰은 유효하나 사용자가 없다(탈퇴) → 401 + AUTH_401_INVALID")
  void 존재하지_않는_사용자_401() {
    String ghost = jwtProvider.createAccessToken("ghost@nowhere.com", Role.USER);

    ResponseEntity<String> res = getWithToken("/api/v1/auth/me", ghost);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(res.getBody()).contains("AUTH_401_INVALID");
  }

  // ── 인가는 살아남았다 — 권한 변경 API 를 토큰으로 (은퇴한 세션 인가 테스트 계승) ──

  @Test
  @DisplayName("권한 변경 API — ADMIN 토큰 200 / USER 토큰 403 / 무토큰 401 (인가 규칙은 인증 방식과 무관)")
  void 권한변경_인가_3종() {
    String body = "{\"email\":\"choon@naver.com\",\"role\":\"ADMIN\"}";
    HttpHeaders json = new HttpHeaders();
    json.setContentType(MediaType.APPLICATION_JSON);

    // 무토큰 → 401
    ResponseEntity<String> anonymous = rest.exchange(base + "/api/v1/users/role",
        HttpMethod.PUT, new HttpEntity<>(body, json), String.class);
    assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // USER 토큰 → 403 (인증은 됐지만 관리자가 아니다)
    HttpHeaders userHeaders = new HttpHeaders();
    userHeaders.setContentType(MediaType.APPLICATION_JSON);
    userHeaders.setBearerAuth(jwtProvider.createAccessToken("choon@naver.com", Role.USER));
    ResponseEntity<String> asUser = rest.exchange(base + "/api/v1/users/role",
        HttpMethod.PUT, new HttpEntity<>(body, userHeaders), String.class);
    assertThat(asUser.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // ADMIN 토큰 → 200 + 변경 반영
    HttpHeaders adminHeaders = new HttpHeaders();
    adminHeaders.setContentType(MediaType.APPLICATION_JSON);
    adminHeaders.setBearerAuth(jwtProvider.createAccessToken("admin@sprintlog.com", Role.ADMIN));
    ResponseEntity<String> asAdmin = rest.exchange(base + "/api/v1/users/role",
        HttpMethod.PUT, new HttpEntity<>(body, adminHeaders), String.class);
    assertThat(asAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(userRepository.findByEmail("choon@naver.com").orElseThrow().getRole())
        .isEqualTo(Role.ADMIN);
  }

  @Test
  @DisplayName("Bearer 형식이 아닌 Authorization 헤더 → 익명 취급 → 보호 API 401")
  void 비_Bearer_헤더_익명() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, "Token abc.def.ghi");   // Bearer 아님 → 필터가 무시

    ResponseEntity<String> res = rest.exchange(base + "/api/v1/auth/me",
        HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(res.getBody()).contains("AUTH_401");
  }

}
