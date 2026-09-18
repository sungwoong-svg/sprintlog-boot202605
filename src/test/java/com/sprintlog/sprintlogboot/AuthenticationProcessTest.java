package com.sprintlog.sprintlogboot;

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
 * [CP119] 인증 프로세스 통합 테스트.
 * 진짜 서버를 띄우고(RANDOM_PORT), HTTP Basic 자격증명을 실어 보내
 * UserDetailsService → DaoAuthenticationProvider → SecurityContext 로 이어지는 인증 전 과정을 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("인증 프로세스 통합 테스트 (HTTP Basic + UserDetailsService + DaoAuthenticationProvider)")
class AuthenticationProcessTest {

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
        // 로그인 대상 사용자 심기 — 아이디=email, 비번="password123"(BCrypt 해시로 저장).
        userRepository.save(new User("김춘식", "choon@naver.com", passwordEncoder.encode("password123")));
        base = "http://localhost:" + port;
    }

    @Test
    @DisplayName("올바른 Basic 자격증명 → whoami 가 실제 사용자(ROLE_USER)를 반환")
    void 로그인_성공() {
        ResponseEntity<String> res = rest
                .withBasicAuth("choon@naver.com", "password123")
                .getForEntity(base + "/api/v1/auth/whoami", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 익명이 아니라 실제 사용자로 인증됐다:
        assertThat(res.getBody()).contains("choon@naver.com");                    // name = email
        assertThat(res.getBody()).contains("ROLE_USER");                          // GrantedAuthority
        assertThat(res.getBody()).contains("UsernamePasswordAuthenticationToken"); // 익명 토큰이 아니다
    }

    @Test
    @DisplayName("틀린 비밀번호 → 401 Unauthorized (인증 실패)")
    void 로그인_실패_잘못된_비번() {
        ResponseEntity<String> res = rest
                .withBasicAuth("choon@naver.com", "wrong-password")
                .getForEntity(base + "/api/v1/auth/whoami", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("자격증명 없음 → 익명으로 접근(permitAll), whoami 는 anonymousUser")
    void 인증없음_익명() {
        ResponseEntity<String> res = rest
                .getForEntity(base + "/api/v1/auth/whoami", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("anonymousUser");
        assertThat(res.getBody()).contains("ROLE_ANONYMOUS");
    }
}
