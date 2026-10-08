package com.sprintlog.sprintlogboot;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintlog.sprintlogboot.domain.Role;
import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import com.sprintlog.sprintlogboot.security.JwtProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * 캐시 엔드포인트를 열면 '비우기 버튼' 이 생긴다 — 누가 누를 수 있는가.
 *
 * <p>Actuator 는 별도 포트(운영 9090)에서 돈다. 테스트는 포트를 0(빈 포트 아무거나)으로 띄우고
 * {@code @LocalManagementPort} 로 받는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0")
@ActiveProfiles("test")
@DisplayName("캐시 비우기는 관리자만")
class CacheEndpointSecurityTest {

  @LocalManagementPort int managementPort;
  @Autowired TestRestTemplate rest;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JwtProvider jwtProvider;

  String 일반_토큰;
  String 관리자_토큰;

  @BeforeEach
  void setUp() {
    // 다른 테스트가 남긴 사용자와 겹치지 않게 이메일을 매번 새로 만든다.
    String 꼬리 = UUID.randomUUID().toString().substring(0, 8);
    User 일반 = userRepository.save(new User("일반", "user-" + 꼬리 + "@sprintlog.com", passwordEncoder.encode("password123")));
    User 관리자 = userRepository.save(new User("관리자", "admin-" + 꼬리 + "@sprintlog.com", passwordEncoder.encode("password123"), Role.ADMIN));
    일반_토큰 = jwtProvider.createAccessToken(일반.getId(), 일반.getEmail(), 일반.getRole());
    관리자_토큰 = jwtProvider.createAccessToken(관리자.getId(), 관리자.getEmail(), 관리자.getRole());
  }

  private int 부른다(HttpMethod 방식, String 토큰) {
    return 부른다(방식, 토큰, "/management/caches");
  }

  private int 부른다(HttpMethod 방식, String 토큰, String 경로) {
    HttpHeaders headers = new HttpHeaders();
    if (토큰 != null) headers.setBearerAuth(토큰);
    int 상태 = rest.exchange("http://localhost:" + managementPort + 경로,
        방식, new HttpEntity<>(headers), String.class).getStatusCode().value();
    System.out.printf("[관찰] %-6s %-6s %-36s → %d%n",
        토큰 == null ? "익명" : (토큰.equals(관리자_토큰) ? "관리자" : "일반"), 방식, 경로, 상태);
    return 상태;
  }

  @Test
  @DisplayName("로그인하지 않으면 보지도 못한다")
  void 익명은_401() {
    assertThat(부른다(HttpMethod.GET, null)).isEqualTo(401);
  }

  @Test
  @DisplayName("일반 사용자는 볼 수는 있지만 비울 수는 없다")
  void 일반은_보기만() {
    assertThat(부른다(HttpMethod.GET, 일반_토큰)).isEqualTo(200);
    assertThat(부른다(HttpMethod.DELETE, 일반_토큰)).isEqualTo(403);
  }

  @Test
  @DisplayName("관리자는 비울 수 있다")
  void 관리자는_비운다() {
    assertThat(부른다(HttpMethod.DELETE, 관리자_토큰)).isEqualTo(204);
  }

  @Test
  @DisplayName("캐시 하나만 비우는 주소도 같이 잠겨 있다 — 규칙의 /** 가 이걸 막는다")
  void 하나만_비우기도_잠겼다() {
    String 하나 = "/management/caches/activitySummary";
    assertThat(부른다(HttpMethod.DELETE, 일반_토큰, 하나)).isEqualTo(403);
    assertThat(부른다(HttpMethod.DELETE, 관리자_토큰, 하나)).isEqualTo(204);
  }
}
