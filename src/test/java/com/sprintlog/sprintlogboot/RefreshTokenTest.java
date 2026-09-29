package com.sprintlog.sprintlogboot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprintlog.sprintlogboot.domain.Role;
import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.repository.RefreshTokenRepository;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
@DisplayName("Refresh Token 통합 테스트 (회전·재사용 감지·무효화)")
class RefreshTokenTest {

  @LocalServerPort
  int port;
  @Autowired
  TestRestTemplate rest;
  @Autowired
  UserRepository userRepository;
  @Autowired
  RefreshTokenRepository refreshTokenRepository;
  @Autowired
  PasswordEncoder passwordEncoder;

  final ObjectMapper mapper = new ObjectMapper();
  String base;
  User choon;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();   // refresh_tokens 는 FK 연쇄 삭제로 함께 정리된다
    choon = userRepository.save(
        new User("김춘식", "choon@naver.com", passwordEncoder.encode("password123")));
    base = "http://localhost:" + port;
  }

  private ResponseEntity<String> post(String url, String body) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    return rest.postForEntity(base + url, new HttpEntity<>(body, h), String.class);
  }

  private JsonNode login() throws Exception {
    return mapper.readTree(post("/api/v1/auth/login",
        """
        {"email":"choon@naver.com","password":"password123"}""").getBody());
  }

  private ResponseEntity<String> refresh(String token) {
    return post("/api/v1/auth/refresh", """
                {"refreshToken":"%s"}""".formatted(token));
  }

  private ResponseEntity<String> getMe(String accessToken) {
    HttpHeaders h = new HttpHeaders();
    h.setBearerAuth(accessToken);
    return rest.exchange(base + "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(h), String.class);
  }

  // ── 1. 발급과 회전 ──────────────────────────────────────

  @Nested
  @DisplayName("발급과 회전")
  class IssueAndRotate {

    @Test
    @DisplayName("로그인하면 Access 와 Refresh 가 함께 온다")
    void 로그인시_둘다_발급() throws Exception {
      JsonNode body = login();

      assertThat(body.get("accessToken").asText()).isNotBlank();
      assertThat(body.get("refreshToken").asText()).isNotBlank();
      assertThat(refreshTokenRepository.countByUserAndRevokedReasonIsNull(choon)).isEqualTo(1);
    }

    @Test
    @DisplayName("⭐ 토큰 원문은 DB 에 저장되지 않는다 — 해시만 남는다")
    void 원문은_저장되지_않는다() throws Exception {
      String raw = login().get("refreshToken").asText();

      // 원문으로는 찾을 수 없다(해시로 저장했으므로). DB 가 털려도 그 값으로 재발급을 못 받는다.
      assertThat(refreshTokenRepository.findByTokenHash(raw)).isEmpty();
      assertThat(refreshTokenRepository.count()).isEqualTo(1);   // 기록 자체는 있다
    }

    @Test
    @DisplayName("재발급하면 새 Access·새 Refresh 가 온다 (회전)")
    void 회전() throws Exception {
      String oldRefresh = login().get("refreshToken").asText();

      ResponseEntity<String> res = refresh(oldRefresh);

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode body = mapper.readTree(res.getBody());
      assertThat(body.get("accessToken").asText()).isNotBlank();
      assertThat(body.get("refreshToken").asText())
          .isNotBlank()
          .isNotEqualTo(oldRefresh);      // 새 것이어야 한다
    }

    @Test
    @DisplayName("⭐ 한 번 쓴 Refresh 는 다시 쓸 수 없다 — 회전의 핵심")
    void 한번_쓰면_죽는다() throws Exception {
      String oldRefresh = login().get("refreshToken").asText();
      refresh(oldRefresh);                    // 1회차 — 성공

      ResponseEntity<String> again = refresh(oldRefresh);   // 같은 토큰 재시도

      assertThat(again.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("재발급받은 Access 로 보호 API 가 열린다")
    void 재발급_Access_동작() throws Exception {
      String refreshToken = login().get("refreshToken").asText();
      String newAccess = mapper.readTree(refresh(refreshToken).getBody())
          .get("accessToken").asText();

      assertThat(getMe(newAccess).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("알 수 없는 토큰으로 재발급 → 401")
    void 알수없는_토큰() {
      assertThat(refresh("this-token-never-existed").getStatusCode())
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  // ── 2. 재사용 감지 ──────────────────────────────────────

  @Nested
  @DisplayName("재사용 감지 (탈취 대응)")
  class ReuseDetection {

    @Test
    @DisplayName("⭐ 이미 쓴 토큰이 다시 오면 그 사용자의 모든 Refresh 가 끊긴다")
    void 재사용_감지시_전체_무효화() throws Exception {
      // 공격자가 Refresh 를 복제한 상황을 만든다.
      String stolen = login().get("refreshToken").asText();

      // 진짜 사용자가 먼저 재발급 → 새 토큰을 받는다(정상)
      String freshRefresh = mapper.readTree(refresh(stolen).getBody())
          .get("refreshToken").asText();
      assertThat(refreshTokenRepository.countByUserAndRevokedReasonIsNull(choon)).isEqualTo(1);

      // 공격자가 복제해둔 옛 토큰을 쓴다 → 이미 쓴 토큰이 다시 온 것 = 탈취 신호
      ResponseEntity<String> attack = refresh(stolen);
      assertThat(attack.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

      // ⭐ 진짜 사용자의 '멀쩡한' 토큰까지 함께 끊긴다 — 계정을 지키기 위한 의도된 동작.
      assertThat(refreshTokenRepository.countByUserAndRevokedReasonIsNull(choon)).isZero();
      assertThat(refresh(freshRefresh).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("무효화 기록은 지우지 않고 남긴다 — 흔적이 있어야 감지가 된다")
    void 흔적을_남긴다() throws Exception {
      String raw = login().get("refreshToken").asText();
      refresh(raw);

      // 회전 후에도 행은 남아 있다(revoked=true). 지웠다면 '이미 쓴 토큰' 을 알아볼 수 없다.
      assertThat(refreshTokenRepository.count()).isEqualTo(2);
      assertThat(refreshTokenRepository.countByUserAndRevokedReasonIsNull(choon)).isEqualTo(1);
    }
  }

  // ── 3. 무효화 3종 ───────────────────────────────────────

  @Nested
  @DisplayName("무효화 전략")
  class Invalidation {

    @Test
    @DisplayName("로그아웃 — 이 기기의 재발급 권리만 끊는다")
    void 로그아웃() throws Exception {
      String refreshToken = login().get("refreshToken").asText();

      assertThat(post("/api/v1/auth/logout", """
                    {"refreshToken":"%s"}""".formatted(refreshToken)).getStatusCode())
          .isEqualTo(HttpStatus.NO_CONTENT);

      assertThat(refresh(refreshToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("⭐ 다른 기기는 살아 있다 — 로그아웃은 '이 기기' 만")
    void 로그아웃은_이_기기만() throws Exception {
      String deviceA = login().get("refreshToken").asText();
      String deviceB = login().get("refreshToken").asText();   // 두 번째 로그인 = 다른 기기

      post("/api/v1/auth/logout", """
                    {"refreshToken":"%s"}""".formatted(deviceA));

      assertThat(refresh(deviceA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(refresh(deviceB).getStatusCode()).isEqualTo(HttpStatus.OK);   // B 는 멀쩡
    }

    @Test
    @DisplayName("⭐ 비밀번호 변경 — 모든 기기가 끊긴다")
    void 비밀번호_변경시_전체_무효화() throws Exception {
      JsonNode first = login();
      String access = first.get("accessToken").asText();
      String deviceA = first.get("refreshToken").asText();
      String deviceB = login().get("refreshToken").asText();

      HttpHeaders h = new HttpHeaders();
      h.setContentType(MediaType.APPLICATION_JSON);
      h.setBearerAuth(access);
      ResponseEntity<String> res = rest.exchange(base + "/api/v1/auth/password", HttpMethod.PUT,
          new HttpEntity<>("""
                            {"currentPassword":"password123","newPassword":"newpassword456"}""", h),
          String.class);

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
      // 두 기기 모두 끊긴다 — "내 계정이 털린 것 같다" 의 1차 대응이므로.
      assertThat(refresh(deviceA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(refresh(deviceB).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

      // ⭐ 비밀번호가 '실제로' 바뀌었는지까지 확인한다.
      //   이 단언이 없으면 @Modifying 의 flushAutomatically 가 빠져도 테스트가 그린으로 통과한다 —
      //   clear() 가 더티 체킹만 해둔 비밀번호 변경을 조용히 버리기 때문이다(무효화는 성공하므로 위 두 줄은 통과).
      HttpHeaders h2 = new HttpHeaders();
      h2.setContentType(MediaType.APPLICATION_JSON);
      assertThat(rest.postForEntity(base + "/api/v1/auth/login", new HttpEntity<>("""
                    {"email":"choon@naver.com","password":"newpassword456"}""", h2), String.class)
          .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 변경 거부 — 토큰만 훔친 공격자는 계정을 못 빼앗는다")
    void 현재_비밀번호_검증() throws Exception {
      String access = login().get("accessToken").asText();

      HttpHeaders h = new HttpHeaders();
      h.setContentType(MediaType.APPLICATION_JSON);
      h.setBearerAuth(access);
      ResponseEntity<String> res = rest.exchange(base + "/api/v1/auth/password", HttpMethod.PUT,
          new HttpEntity<>("""
                            {"currentPassword":"wrong-password","newPassword":"newpassword456"}""", h),
          String.class);

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("⭐ 권한 변경 = 강제 로그아웃 — 토픽 내내 '잃었다' 던 그 수단이 돌아온다")
    void 권한_변경시_강제_로그아웃() throws Exception {
      String userRefresh = login().get("refreshToken").asText();

      User admin = userRepository.save(
          new User("관리자", "admin@sprintlog.com", passwordEncoder.encode("admin123"), Role.ADMIN));
      String adminAccess = mapper.readTree(post("/api/v1/auth/login", """
                    {"email":"admin@sprintlog.com","password":"admin123"}""").getBody())
          .get("accessToken").asText();

      HttpHeaders h = new HttpHeaders();
      h.setContentType(MediaType.APPLICATION_JSON);
      h.setBearerAuth(adminAccess);
      ResponseEntity<String> res = rest.exchange(base + "/api/v1/users/role", HttpMethod.PUT,
          new HttpEntity<>("""
                            {"email":"choon@naver.com","role":"ADMIN"}""", h), String.class);

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
      // 재발급이 막힌다 → 기존 Access 가 만료되는 순간 완전히 끊긴다.
      //   "즉시" 는 아니지만 **상한이 있는** 무효화다(Access 수명 = 상한).
      assertThat(refresh(userRefresh).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(admin.getId()).isNotNull();
    }
  }
}
