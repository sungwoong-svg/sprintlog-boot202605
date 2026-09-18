package com.sprintlog.sprintlogboot;

import com.sprintlog.sprintlogboot.domain.Role;
import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import com.sprintlog.sprintlogboot.security.CustomUserDetails;
import com.sprintlog.sprintlogboot.service.UserService;
import com.sprintlog.sprintlogboot.support.CsrfTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [CP128] 세션 기반 인가 종합 테스트.
 *   ① 권한 변경 API 는 관리자만(URL 인가 + @PreAuthorize 두 겹): 관리자 200 · 비관리자 403 · 미인증 401.
 *   ② 권한을 바꾸면 그 사용자의 세션이 SessionRegistry 에서 만료된다(프로그래매틱 세션 무효화).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("세션 기반 인가 종합 (권한 변경 관리자 전용 + 권한 변경 시 세션 만료)")
class SessionAuthorizationTest {

    @LocalServerPort
    int port;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    SessionRegistry sessionRegistry;
    @Autowired
    UserService userService;

    String base;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(new User("김춘식", "choon@naver.com", passwordEncoder.encode("password123")));                 // USER
        userRepository.save(new User("홍길동", "hong@gmail.com", passwordEncoder.encode("password123")));                  // USER
        userRepository.save(new User("관리자", "admin@sprintlog.com", passwordEncoder.encode("admin123"), Role.ADMIN));    // ADMIN
        base = "http://localhost:" + port;
    }

    private RestTemplate noRedirect() {
        HttpClient jdk = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        RestTemplate rt = new RestTemplate(new JdkClientHttpRequestFactory(jdk));
        rt.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override public boolean hasError(org.springframework.http.client.ClientHttpResponse r) { return false; }
            @Override public void handleError(org.springframework.http.client.ClientHttpResponse r) { }
        });
        return rt;
    }

    /** 폼 로그인(CSRF 토큰 동반) → JSESSIONID 쿠키 조각("JSESSIONID=...") 반환. */
    private String login(RestTemplate rt, String email, String password) {
        String csrf = CsrfTestSupport.fetchToken(rt, base);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", email);
        form.add("password", password);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        headers.add("X-XSRF-TOKEN", csrf);
        ResponseEntity<Void> res = rt.postForEntity(base + "/login", new HttpEntity<>(form, headers), Void.class);
        return "JSESSIONID=" + CsrfTestSupport.cookieValue(res.getHeaders(), "JSESSIONID");
    }

    /** 로그인 세션 + CSRF 토큰을 실어 PUT /api/v1/auth/role 로 권한 변경 요청(미인증이면 sessionCookie=null). */
    private ResponseEntity<String> putRole(RestTemplate rt, String sessionCookie, String targetEmail, Role role) {
        String csrf = CsrfTestSupport.fetchToken(rt, base);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String cookie = "XSRF-TOKEN=" + csrf + (sessionCookie != null ? "; " + sessionCookie : "");
        headers.add(HttpHeaders.COOKIE, cookie);
        headers.add("X-XSRF-TOKEN", csrf);
        String body = "{\"email\":\"" + targetEmail + "\",\"role\":\"" + role.name() + "\"}";
        return rt.exchange(base + "/api/v1/users/role", HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
    }

    /** SessionRegistry 에서 email 의 '살아있는(만료 안 된)' 세션 목록. */
    private List<SessionInformation> activeSessionsOf(String email) {
        List<SessionInformation> result = new ArrayList<>();
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof CustomUserDetails d && d.getUsername().equals(email)) {
                result.addAll(sessionRegistry.getAllSessions(principal, false));
            }
        }
        return result;
    }

    @Test
    @DisplayName("권한 변경: 관리자 → 200, 일반 사용자 → 403, 미인증 → 401")
    void 권한변경_관리자만() {
        // 관리자 → 200
        RestTemplate adminRt = noRedirect();
        String adminSession = login(adminRt, "admin@sprintlog.com", "admin123");
        assertThat(putRole(adminRt, adminSession, "choon@naver.com", Role.ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // 일반 사용자(choon) → 403 (인증은 됐지만 ADMIN 아님)
        RestTemplate userRt = noRedirect();
        String userSession = login(userRt, "hong@gmail.com", "password123");
        assertThat(putRole(userRt, userSession, "choon@naver.com", Role.ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // 미인증 → 401
        RestTemplate anonRt = noRedirect();
        assertThat(putRole(anonRt, null, "choon@naver.com", Role.ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("권한을 바꾸면 그 사용자의 세션이 SessionRegistry 에서 만료된다")
    void 권한변경시_세션만료() {
        // 1) choon 로그인 → 세션이 SessionRegistry 에 등록(살아있음)
        RestTemplate choonRt = noRedirect();
        login(choonRt, "choon@naver.com", "password123");
        assertThat(activeSessionsOf("choon@naver.com")).as("로그인 직후 살아있는 세션").isNotEmpty();

        // 2) 관리자가 choon 의 권한을 변경 → UserService 가 choon 의 세션을 만료시킨다
        userService.changeRole("choon@naver.com", Role.ADMIN);

        // 3) 이제 choon 의 살아있는 세션은 없다(전부 만료됨)
        assertThat(activeSessionsOf("choon@naver.com")).as("권한 변경 후 살아있는 세션").isEmpty();
    }
}
