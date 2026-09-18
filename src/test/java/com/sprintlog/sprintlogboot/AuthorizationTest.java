package com.sprintlog.sprintlogboot;

import com.sprintlog.sprintlogboot.domain.Role;
import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [CP120] 인가(authorization) 통합 테스트.
 * 경로별 규칙(AuthorizationManager)과 RoleHierarchy(ADMIN⊇USER)가 실제로 동작하는지 검증한다.
 *   /api/v1/admin/**  → ROLE_ADMIN 만
 *   /api/v1/me/**     → ROLE_USER (ADMIN 도 계층상 포함)
 * 401(인증 필요) vs 403(인증했지만 권한 부족) 의 차이도 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("인가 통합 테스트 (AuthorizationManager + RoleHierarchy)")
class AuthorizationTest {

    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    String base;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(new User("관리자", "admin@sprintlog.com", passwordEncoder.encode("admin123"), Role.ADMIN));
        userRepository.save(new User("김춘식", "choon@naver.com", passwordEncoder.encode("password123"))); // 기본 USER
        base = "http://localhost:" + port;
    }

    @Test
    @DisplayName("ADMIN 계정 → 관리자 경로 접근 허용(200)")
    void 관리자_관리자경로_허용() {
        ResponseEntity<String> res = rest
                .withBasicAuth("admin@sprintlog.com", "admin123")
                .getForEntity(base + "/api/v1/admin/dashboard/detail", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("관리자 전용");
    }

    @Test
    @DisplayName("USER 계정 → 관리자 경로 거부(403, 인증은 됐지만 권한 부족)")
    void 일반유저_관리자경로_거부_403() {
        ResponseEntity<String> res = rest
                .withBasicAuth("choon@naver.com", "password123")
                .getForEntity(base + "/api/v1/admin/dashboard/detail", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("자격증명 없음 → 관리자 경로 401(인증 필요)")
    void 인증없음_관리자경로_401() {
        ResponseEntity<String> res = rest
                .getForEntity(base + "/api/v1/admin/dashboard/detail", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("USER 계정 → 사용자 경로 허용(200)")
    void 일반유저_사용자경로_허용() {
        ResponseEntity<String> res = rest
                .withBasicAuth("choon@naver.com", "password123")
                .getForEntity(base + "/api/v1/me/profile", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("ADMIN 계정 → 사용자 경로도 허용(RoleHierarchy: ADMIN ⊇ USER)")
    void 관리자_사용자경로_허용_계층() {
        ResponseEntity<String> res = rest
                .withBasicAuth("admin@sprintlog.com", "admin123")
                .getForEntity(base + "/api/v1/me/profile", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
