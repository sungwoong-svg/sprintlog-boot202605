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
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [CP123] 세션 기반(폼 로그인) 통합 테스트.
 *   폼으로 한 번 로그인하면 JSESSIONID 세션 쿠키가 발급되고, 이후 요청은 그 쿠키만으로 인증이 유지된다.
 *   (TestRestTemplate 은 기본적으로 리다이렉트를 따라가지 않아, 로그인 응답의 302 + Set-Cookie 를 그대로 관찰할 수 있다.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("세션 기반 폼 로그인 통합 테스트 (formLogin + JSESSIONID)")
class SessionLoginTest {

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
        userRepository.save(new User("김춘식", "choon@naver.com", passwordEncoder.encode("password123")));
        base = "http://localhost:" + port;
    }

    /**
     * 폼 로그인 요청을 보내고 302 응답을 그대로 돌려준다.
     * 리다이렉트를 '따라가지 않는' 클라이언트로 보내야 302 + Set-Cookie(JSESSIONID) 를 관찰할 수 있다
     * (TestRestTemplate 은 리다이렉트를 따라가버려 302 를 놓친다).
     */
    private ResponseEntity<Void> formLogin(String username, String password) {
        HttpClient jdk = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        RestTemplate noRedirect = new RestTemplate(new JdkClientHttpRequestFactory(jdk));
        // [CP127b] 로그인 실패가 이제 401 이라, 4xx 에서 예외를 던지지 않도록 에러 핸들러를 끈다(상태코드 관찰용).
        noRedirect.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override public boolean hasError(org.springframework.http.client.ClientHttpResponse response) { return false; }
            @Override public void handleError(org.springframework.http.client.ClientHttpResponse response) { }
        });

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("password", password);

        // [CP127c] CSRF 를 켰으므로 로그인(POST)도 토큰이 필요하다: 먼저 발급받아 쿠키+헤더로 함께 보낸다.
        String csrf = com.sprintlog.sprintlogboot.support.CsrfTestSupport.fetchToken(noRedirect, base);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        headers.add("X-XSRF-TOKEN", csrf);

        return noRedirect.postForEntity(base + "/login", new HttpEntity<>(form, headers), Void.class);
    }

    @Test
    @DisplayName("폼 로그인 성공 → [CP127b] 200 + JSESSIONID 세션 쿠키 발급(리다이렉트 아님)")
    void 폼로그인_성공_세션쿠키() {
        ResponseEntity<Void> res = formLogin("choon@naver.com", "password123");

        // [CP127b] SPA 방식 — 성공 시 302 리다이렉트가 아니라 200(JSON). 세션 쿠키는 여전히 발급된다.
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK); // 200
        assertThat(res.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("JSESSIONID");
    }

    @Test
    @DisplayName("발급받은 세션 쿠키만으로 whoami 가 실제 사용자로 인증된다(자격증명 재전송 X)")
    void 세션쿠키로_인증유지() {
        // 1) 로그인해서 세션 쿠키 확보
        String setCookie = formLogin("choon@naver.com", "password123")
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        String sessionCookie = setCookie.split(";")[0]; // "JSESSIONID=xxxx"

        // 2) 그 쿠키만 실어 whoami 호출(Basic 헤더 없음!)
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        ResponseEntity<String> who = rest.exchange(
                base + "/api/v1/auth/whoami", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(who.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(who.getBody()).contains("choon@naver.com");                     // 세션으로 인증됨
        assertThat(who.getBody()).contains("UsernamePasswordAuthenticationToken"); // 익명 아님
    }

    @Test
    @DisplayName("세션 쿠키 없이 whoami → 익명(anonymousUser)")
    void 세션없으면_익명() {
        ResponseEntity<String> who = rest.getForEntity(base + "/api/v1/auth/whoami", String.class);
        assertThat(who.getBody()).contains("anonymousUser");
    }

    @Test
    @DisplayName("틀린 비밀번호 → [CP127b] 401(JSON) 응답(리다이렉트 아님)")
    void 폼로그인_실패() {
        ResponseEntity<Void> res = formLogin("choon@naver.com", "wrong-password");

        // [CP127b] SPA 방식 — 실패 시 로그인 페이지 리다이렉트가 아니라 401.
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED); // 401
    }
}
