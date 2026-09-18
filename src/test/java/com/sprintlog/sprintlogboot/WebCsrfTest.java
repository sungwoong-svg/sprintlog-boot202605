package com.sprintlog.sprintlogboot;

import com.sprintlog.sprintlogboot.dto.request.SignUpRequest;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import com.sprintlog.sprintlogboot.support.CsrfTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [CP127c] CSRF 방어가 '실제로' 동작하는지 검증한다.
 *   - GET /csrf-token → XSRF-TOKEN 쿠키 발급.
 *   - 토큰 없는 상태변경(POST) → 막힌다(생성되지 않음).
 *   - 토큰을 실은 상태변경(POST) → 통과한다(생성됨).
 *   - GET(안전 메서드) → 토큰 없이도 동작.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("CSRF 방어 통합 테스트 (CookieCsrfTokenRepository + SpaCsrfTokenRequestHandler)")
class WebCsrfTest {

    @LocalServerPort
    int port;
    @Autowired
    UserRepository userRepository;

    String base;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        base = "http://localhost:" + port;
    }

    /** 리다이렉트 미추적 + 4xx 예외 안 던지는 클라이언트(상태코드·리다이렉트 관찰용). */
    private RestTemplate client() {
        HttpClient jdk = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        RestTemplate rt = new RestTemplate(new JdkClientHttpRequestFactory(jdk));
        rt.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override public boolean hasError(org.springframework.http.client.ClientHttpResponse r) { return false; }
            @Override public void handleError(org.springframework.http.client.ClientHttpResponse r) { }
        });
        return rt;
    }

    @Test
    @DisplayName("GET /csrf-token → 204 + XSRF-TOKEN 쿠키 발급")
    void csrf토큰_발급() {
        ResponseEntity<Void> r = client().getForEntity(base + "/api/v1/auth/csrf-token", Void.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(CsrfTestSupport.cookieValue(r.getHeaders(), "XSRF-TOKEN")).isNotNull();
    }

    @Test
    @DisplayName("토큰 없이 상태변경(POST) → 막혀서 생성되지 않는다")
    void 토큰없는_POST_차단() {
        SignUpRequest req = new SignUpRequest("csrf@sprintlog.com", "password123", "씨에스");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> res = client().postForEntity(
                base + "/api/v1/users", new HttpEntity<>(req, headers), String.class);

        // 성공(2xx)이 아니어야 하고, 무엇보다 DB 에 사용자가 만들어지지 않아야 한다(핵심 보안 속성).
        assertThat(res.getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(userRepository.findByEmail("csrf@sprintlog.com")).isEmpty();
    }

    @Test
    @DisplayName("토큰을 실으면 상태변경(POST)이 통과한다 → 201 + 생성됨")
    void 토큰있는_POST_허용() {
        RestTemplate rt = client();
        String csrf = CsrfTestSupport.fetchToken(rt, base);

        SignUpRequest req = new SignUpRequest("ok@sprintlog.com", "password123", "허용");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        headers.add("X-XSRF-TOKEN", csrf);

        ResponseEntity<String> res = rt.postForEntity(
                base + "/api/v1/users", new HttpEntity<>(req, headers), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(userRepository.findByEmail("ok@sprintlog.com")).isPresent();
    }

    @Test
    @DisplayName("GET(안전 메서드)은 토큰 없이도 동작한다")
    void GET_은_토큰불요() {
        ResponseEntity<String> res = client().getForEntity(base + "/api/v1/activities", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
