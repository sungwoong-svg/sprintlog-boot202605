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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [CP125] 세션 관리 설정 통합 테스트.
 *   요청에 담긴 JSESSIONID 가 '이미 만료·무효' 인 세션이면, invalidSessionUrl 로 리다이렉트되는지 검증한다.
 *   (리다이렉트를 따라가지 않는 클라이언트로 302 를 관찰한다.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("세션 관리 설정 통합 테스트 (invalidSessionUrl)")
class SessionConfigTest {

    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;

    String base;

    @BeforeEach
    void setUp() {
        base = "http://localhost:" + port;
    }

    private RestTemplate noRedirect() {
        HttpClient jdk = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return new RestTemplate(new JdkClientHttpRequestFactory(jdk));
    }

    @Test
    @DisplayName("무효·만료된 JSESSIONID 로 접근 → invalidSessionUrl(/login.html?expired) 로 리다이렉트")
    void 무효세션_리다이렉트() {
        // 서버에 존재하지 않는 가짜 세션 ID 를 쿠키로 실어 보낸다(= 만료돼 사라진 세션에 옛 쿠키로 접근하는 상황).
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "JSESSIONID=INVALID_FAKE_SESSION_0000000000");

        ResponseEntity<Void> res = noRedirect().exchange(
                base + "/api/v1/auth/whoami", HttpMethod.GET, new HttpEntity<>(headers), Void.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND); // 302
        assertThat(res.getHeaders().getLocation().toString()).contains("/login.html?expired");
    }

    @Test
    @DisplayName("세션 쿠키 없이 접근 → 무효 세션 아님(익명으로 정상 응답)")
    void 세션쿠키없음_정상() {
        ResponseEntity<String> res = rest.getForEntity(base + "/api/v1/auth/whoami", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("anonymousUser");
    }
}
