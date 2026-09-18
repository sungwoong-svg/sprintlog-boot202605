package com.sprintlog.sprintlogboot;

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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [CP122] 웹 보안 방어 통합 테스트 — CSP(XSS 완화) 헤더와 CORS 정책이 실제 응답에 적용되는지 검증.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("웹 보안 방어 통합 테스트 (CSP 헤더 + CORS)")
class WebSecurityDefenseTest {

    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;

    String whoami;

    @BeforeEach
    void setUp() {
        whoami = "http://localhost:" + port + "/api/v1/auth/whoami";
    }

    @Test
    @DisplayName("모든 응답에 Content-Security-Policy 헤더가 붙는다(XSS 완화)")
    void CSP_헤더가_붙는다() {
        ResponseEntity<String> res = rest.getForEntity(whoami, String.class);
        String csp = res.getHeaders().getFirst("Content-Security-Policy");
        assertThat(csp).isNotNull().contains("default-src 'self'");
        // Spring Security 기본 보안 헤더도 함께 붙는다.
        assertThat(res.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("허용된 출처(localhost:3000)의 요청에 CORS 허용 헤더를 응답한다")
    void CORS_허용_출처() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "http://localhost:3000");

        ResponseEntity<String> res = rest.exchange(
                whoami, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(res.getHeaders().getFirst("Access-Control-Allow-Origin"))
                .isEqualTo("http://localhost:3000");
    }

    @Test
    @DisplayName("허용 안 한 출처(:9999)엔 CORS 허용 헤더를 주지 않는다 → 브라우저가 차단")
    void CORS_비허용_출처는_차단() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "http://localhost:9999"); // 허용 목록(:3000, :63342)에 없는 출처

        ResponseEntity<String> res = rest.exchange(
                whoami, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        // Access-Control-Allow-Origin 이 없으므로 브라우저는 이 응답을 JS 에 넘기지 않고 막는다.
        assertThat(res.getHeaders().getFirst("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    @DisplayName("CORS preflight(OPTIONS)에 허용 메서드를 응답한다")
    void CORS_preflight() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "http://localhost:3000");
        headers.set("Access-Control-Request-Method", "POST");

        ResponseEntity<Void> res = rest.exchange(
                whoami, HttpMethod.OPTIONS, new HttpEntity<>(headers), Void.class);

        assertThat(res.getHeaders().getFirst("Access-Control-Allow-Origin"))
                .isEqualTo("http://localhost:3000");
        assertThat(res.getHeaders().getAccessControlAllowMethods())
                .contains(HttpMethod.POST);
    }
}
