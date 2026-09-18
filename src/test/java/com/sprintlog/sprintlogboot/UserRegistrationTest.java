package com.sprintlog.sprintlogboot;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.dto.request.SignUpRequest;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import java.net.http.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * [CP123a] 회원가입 통합 테스트.
 *   가입 성공(201·해시 저장·비번 미노출) / 이메일 중복(409) / 검증 실패(400) +
 *   ★ 가입한 계정으로 실제 폼 로그인이 되는 端to端(가입 ↔ 로그인 연결).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("회원가입 통합 테스트 (POST /api/v1/users + 가입→로그인 端to端)")
class UserRegistrationTest {

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
        base = "http://localhost:" + port;
    }

    /** [CP127c] GET /csrf-token 으로 XSRF-TOKEN(raw) 발급. (TestRestTemplate 은 4xx 에서 예외를 안 던진다.) */
    private String csrfToken() {
        ResponseEntity<Void> r = rest.getForEntity(base + "/api/v1/auth/csrf-token", Void.class);
        return com.sprintlog.sprintlogboot.support.CsrfTestSupport.cookieValue(r.getHeaders(), "XSRF-TOKEN");
    }

    /** [CP127c] 회원가입(POST=상태변경)은 CSRF 토큰이 필요하다 → 토큰을 쿠키+헤더로 실어 보낸다. */
    private ResponseEntity<String> signup(SignUpRequest req) {
        String csrf = csrfToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        headers.add("X-XSRF-TOKEN", csrf);
        return rest.postForEntity(base + "/api/v1/users", new HttpEntity<>(req, headers), String.class);
    }

    @Test
    @DisplayName("회원가입 성공 → 201, 비번은 해시로 저장되고 응답엔 비번이 없다")
    void 회원가입_성공() {
        SignUpRequest req = new SignUpRequest("new@sprintlog.com", "password123", "새신자");

        ResponseEntity<String> res = signup(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);            // 201
        assertThat(res.getBody()).contains("new@sprintlog.com").contains("새신자");
        assertThat(res.getBody()).doesNotContain("password");                     // 비번(해시)조차 응답에 없음

        // DB 확인: 평문이 아니라 BCrypt 해시로 저장됐다.
        User saved = userRepository.findByEmail("new@sprintlog.com").orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
    }

    @Test
    @DisplayName("이메일 중복 → 409 (code U001)")
    void 이메일_중복_409() {
        SignUpRequest req = new SignUpRequest("dup@sprintlog.com", "password123", "먼저");
        signup(req); // 1번째 가입

        SignUpRequest again = new SignUpRequest("dup@sprintlog.com", "password123", "나중");
        ResponseEntity<String> res = signup(again);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);           // 409
        assertThat(res.getBody()).contains("U001");
    }

    @Test
    @DisplayName("검증 실패(형식 틀린 이메일·짧은 비번) → 400")
    void 검증_실패_400() {
        SignUpRequest bad = new SignUpRequest("not-an-email", "short", "x");

        ResponseEntity<String> res = signup(bad);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);        // 400
    }

    @Test
    @DisplayName("端to端 — 가입한 계정으로 폼 로그인이 된다(가입↔로그인 연결)")
    void 가입후_로그인() {
        // 1) 회원가입
        SignUpRequest req = new SignUpRequest("e2e@sprintlog.com", "password123", "이투이");
        assertThat(signup(req).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // 2) 가입한 그 계정으로 폼 로그인 → [CP127b] 200 + JSESSIONID (리다이렉트 미추적 클라이언트로)
        HttpClient jdk = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        RestTemplate noRedirect = new RestTemplate(new JdkClientHttpRequestFactory(jdk));

        // [CP127c] 로그인도 CSRF 토큰이 필요하다.
        String csrf = com.sprintlog.sprintlogboot.support.CsrfTestSupport.fetchToken(noRedirect, base);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", "e2e@sprintlog.com");
        form.add("password", "password123");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        headers.add("X-XSRF-TOKEN", csrf);

        ResponseEntity<Void> login = noRedirect.postForEntity(
                base + "/login", new HttpEntity<>(form, headers), Void.class);

        // [CP127b] SPA 방식 — 로그인 성공이 302 리다이렉트에서 200(JSON)으로 바뀌었다.
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);               // 200 (성공)
        // Set-Cookie 에 JSESSIONID·XSRF-TOKEN 이 함께 오므로 목록 전체에서 JSESSIONID 존재를 확인.
        assertThat(login.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(c -> c.contains("JSESSIONID"));
    }
}
