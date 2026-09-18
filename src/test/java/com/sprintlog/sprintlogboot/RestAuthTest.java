package com.sprintlog.sprintlogboot;

import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [CP127b] SPA(REST) 방식 인증 통합 테스트.
 *   로그인 성공/실패가 리다이렉트가 아니라 JSON(200/401)으로 응답하고,
 *   현재 사용자 조회(/me)가 @AuthenticationPrincipal 로 동작하며, 로그아웃이 204 를 반환하는지 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("SPA 방식 REST 인증 통합 테스트 (JSON 핸들러 + @AuthenticationPrincipal)")
class RestAuthTest {

    @LocalServerPort
    int port;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    String base;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(new User("김춘식", "choon@naver.com", passwordEncoder.encode("password123")));
        base = "http://localhost:" + port;
    }

    private RestTemplate noRedirect() {
        HttpClient jdk = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        RestTemplate template = new RestTemplate(new JdkClientHttpRequestFactory(jdk));
        // 기본 RestTemplate 은 4xx/5xx 에서 예외를 던진다 → 401 응답 자체를 관찰하려면 에러 핸들러를 끈다.
        template.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override public boolean hasError(org.springframework.http.client.ClientHttpResponse response) { return false; }
            @Override public void handleError(org.springframework.http.client.ClientHttpResponse response) { }
        });
        return template;
    }

    private ResponseEntity<String> login(String username, String password) {
        RestTemplate rt = noRedirect();
        String csrf = com.sprintlog.sprintlogboot.support.CsrfTestSupport.fetchToken(rt, base); // [CP127c]
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("password", password);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf); // [CP127c] CSRF 토큰 쿠키+헤더
        headers.add("X-XSRF-TOKEN", csrf);
        return rt.postForEntity(base + "/login", new HttpEntity<>(form, headers), String.class);
    }

    @Test
    @DisplayName("로그인 성공 → 200 + 사용자 JSON(비밀번호 없음)")
    void 로그인성공_JSON() {
        ResponseEntity<String> res = login("choon@naver.com", "password123");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("JSESSIONID");
        assertThat(res.getBody()).contains("choon@naver.com");   // UserResponse
        assertThat(res.getBody()).contains("USER");              // role
        assertThat(res.getBody()).doesNotContain("password");    // 비밀번호(해시)는 절대 노출 X
    }

    @Test
    @DisplayName("로그인 실패 → 401 + ProblemDetail JSON(리다이렉트 아님)")
    void 로그인실패_401() {
        ResponseEntity<String> res = login("choon@naver.com", "wrong-password");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody()).contains("AUTH_LOGIN_FAILED");
    }

    @Test
    @DisplayName("세션 쿠키로 /api/v1/auth/me → 200 + 현재 사용자(@AuthenticationPrincipal)")
    void me_인증되면_현재사용자() {
        // [CP127c] 로그인 응답 Set-Cookie 에 JSESSIONID·XSRF-TOKEN 이 함께 오므로 JSESSIONID 만 골라낸다.
        var loginRes = login("choon@naver.com", "password123");
        String sessionCookie = "JSESSIONID=" +
                com.sprintlog.sprintlogboot.support.CsrfTestSupport.cookieValue(loginRes.getHeaders(), "JSESSIONID");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        ResponseEntity<String> me = noRedirect().exchange(
                base + "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).contains("choon@naver.com");
        assertThat(me.getBody()).doesNotContain("password");
    }

    @Test
    @DisplayName("세션 없이 /api/v1/auth/me → 401(인증 필요)")
    void me_미인증_401() {
        ResponseEntity<String> me = noRedirect().exchange(
                base + "/api/v1/auth/me", HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("로그아웃 → 204(No Content)")
    void 로그아웃_204() {
        var loginRes = login("choon@naver.com", "password123");
        String sessionCookie = "JSESSIONID=" +
                com.sprintlog.sprintlogboot.support.CsrfTestSupport.cookieValue(loginRes.getHeaders(), "JSESSIONID");

        // [CP127c] 로그아웃(POST)도 상태변경이라 CSRF 토큰이 필요하다 → 세션 쿠키와 함께 토큰 쿠키+헤더를 싣는다.
        RestTemplate rt = noRedirect();
        String csrf = com.sprintlog.sprintlogboot.support.CsrfTestSupport.fetchToken(rt, base);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie + "; XSRF-TOKEN=" + csrf);
        headers.add("X-XSRF-TOKEN", csrf);
        ResponseEntity<Void> logout = rt.postForEntity(
                base + "/logout", new HttpEntity<>(headers), Void.class);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT); // 204
    }
}
