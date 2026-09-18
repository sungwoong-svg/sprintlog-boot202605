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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [CP126] 동시 세션 제어 통합 테스트.
 *   maximumSessions(1) + maxSessionsPreventsLogin(false) →
 *   같은 계정으로 두 번째 로그인하면 '첫 번째 세션이 만료' 되고, 그 만료된 세션으로 접근하면 expiredUrl 로 밀린다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("동시 세션 제어 통합 테스트 (maximumSessions=1)")
class ConcurrentSessionTest {

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

    /** 폼 로그인 → 302 응답의 JSESSIONID 쿠키("JSESSIONID=xxxx") 를 돌려준다(리다이렉트 미추적). */
    private String login() {
        HttpClient jdk = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        RestTemplate noRedirect = new RestTemplate(new JdkClientHttpRequestFactory(jdk));

        String csrf = com.sprintlog.sprintlogboot.support.CsrfTestSupport.fetchToken(noRedirect, base); // [CP127c]
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", "choon@naver.com");
        form.add("password", "password123");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf); // [CP127c] CSRF 토큰 쿠키+헤더
        headers.add("X-XSRF-TOKEN", csrf);

        ResponseEntity<Void> res = noRedirect.postForEntity(base + "/login", new HttpEntity<>(form, headers), Void.class);
        // 로그인 응답의 Set-Cookie 에는 JSESSIONID 와 (회전된) XSRF-TOKEN 이 함께 온다 → 첫 조각(JSESSIONID)만 사용.
        return res.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .map(c -> c.split(";")[0]).filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElseThrow();
    }

    private ResponseEntity<String> whoamiWith(String sessionCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        // 리다이렉트를 따라가지 않아야 만료 세션의 302(expiredUrl)를 관찰할 수 있다.
        HttpClient jdk = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        RestTemplate noRedirect = new RestTemplate(new JdkClientHttpRequestFactory(jdk));
        return noRedirect.exchange(base + "/api/v1/auth/whoami", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("두 번째 로그인 → 첫 세션 만료. 첫 세션으로 접근하면 expiredUrl 로 밀린다")
    void 동시로그인_이전세션_만료() {
        // 1) 첫 로그인 → 세션1
        String session1 = login();
        // 첫 세션은 아직 유효 — whoami 가 실제 사용자(200)
        assertThat(whoamiWith(session1).getStatusCode()).isEqualTo(HttpStatus.OK);

        // 2) 같은 계정으로 두 번째 로그인 → 세션2 (maximumSessions=1 이라 세션1이 만료된다)
        String session2 = login();
        assertThat(whoamiWith(session2).getStatusCode()).isEqualTo(HttpStatus.OK); // 새 세션은 정상

        // 3) 만료된 첫 세션(session1)으로 접근 → expiredUrl(/login.html?expired) 로 리다이렉트
        ResponseEntity<String> res = whoamiWith(session1);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND); // 302
        assertThat(res.getHeaders().getLocation().toString()).contains("/login.html?expired");
    }
}
