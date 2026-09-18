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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [CP127] Remember-Me(자동 로그인) 통합 테스트.
 *   "로그인 유지" 를 체크하고 로그인하면 Remember-Me 쿠키가 발급되고,
 *   세션(JSESSIONID) 없이 그 쿠키만으로도 다시 로그인 상태가 복원되는지 검증한다.
 *   또한 영구 토큰이 DB(persistent_logins)에 실제로 저장되는지 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Remember-Me 자동 로그인 통합 테스트")
class RememberMeTest {

    @LocalServerPort
    int port;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JdbcTemplate jdbcTemplate;

    String base;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        jdbcTemplate.update("delete from persistent_logins");
        userRepository.save(new User("김춘식", "choon@naver.com", passwordEncoder.encode("password123")));
        base = "http://localhost:" + port;
    }

    /** 리다이렉트를 따라가지 않는 클라이언트(302 의 Set-Cookie 를 관찰하려면 필수). */
    private RestTemplate noRedirect() {
        HttpClient jdk = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return new RestTemplate(new JdkClientHttpRequestFactory(jdk));
    }

    /** "로그인 유지" 체크(remember-me=true) 로 폼 로그인 → 302 응답의 Set-Cookie 목록을 돌려준다. */
    private List<String> loginWithRememberMe() {
        RestTemplate rt = noRedirect();
        String csrf = com.sprintlog.sprintlogboot.support.CsrfTestSupport.fetchToken(rt, base); // [CP127c]
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", "choon@naver.com");
        form.add("password", "password123");
        form.add("remember-me", "true");                       // 체크박스 체크 상태
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf); // [CP127c] CSRF 토큰 쿠키+헤더
        headers.add("X-XSRF-TOKEN", csrf);

        ResponseEntity<Void> res = rt.postForEntity(base + "/login", new HttpEntity<>(form, headers), Void.class);
        return res.getHeaders().get(HttpHeaders.SET_COOKIE);
    }

    /** Set-Cookie 목록에서 이름이 name 인 쿠키의 "name=value" 조각을 찾는다(없으면 null). */
    private String cookie(List<String> setCookies, String name) {
        if (setCookies == null) return null;
        return setCookies.stream()
                .map(c -> c.split(";")[0])                     // "remember-me=xxx; Path=/..." → "remember-me=xxx"
                .filter(c -> c.startsWith(name + "="))
                .findFirst().orElse(null);
    }

    private ResponseEntity<String> whoamiWith(String cookieHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieHeader);
        return noRedirect().exchange(base + "/api/v1/auth/whoami", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("'로그인 유지' 로그인 → 세션 없이 Remember-Me 쿠키만으로 인증이 복원된다")
    void 자동로그인_쿠키만으로_인증복원() {
        // 1) '로그인 유지' 로 로그인 → JSESSIONID 와 remember-me 쿠키가 함께 발급된다
        List<String> setCookies = loginWithRememberMe();
        String rememberMeCookie = cookie(setCookies, "remember-me");
        assertThat(cookie(setCookies, "JSESSIONID")).isNotNull();
        assertThat(rememberMeCookie).as("Remember-Me 쿠키가 발급돼야 한다").isNotNull();

        // 2) 영구 토큰이 DB(persistent_logins)에 실제로 저장됐다
        Integer tokenRows = jdbcTemplate.queryForObject(
                "select count(*) from persistent_logins where username = ?", Integer.class, "choon@naver.com");
        assertThat(tokenRows).isEqualTo(1);

        // 3) 세션 쿠키(JSESSIONID) 없이 '오직 Remember-Me 쿠키' 만으로 whoami 요청
        ResponseEntity<String> res = whoamiWith(rememberMeCookie);

        // 4) 익명이 아니라 실제 사용자로 인증이 복원된다(RememberMeAuthenticationToken)
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("choon@naver.com");
        assertThat(res.getBody()).contains("RememberMe");       // authenticationType = RememberMeAuthenticationToken
        assertThat(res.getBody()).doesNotContain("anonymousUser");
    }

    @Test
    @DisplayName("'로그인 유지' 를 체크하지 않으면 Remember-Me 쿠키가 발급되지 않는다")
    void 미체크시_쿠키없음() {
        RestTemplate rt = noRedirect();
        String csrf = com.sprintlog.sprintlogboot.support.CsrfTestSupport.fetchToken(rt, base); // [CP127c]
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", "choon@naver.com");
        form.add("password", "password123");                    // remember-me 파라미터 없음
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);  // [CP127c]
        headers.add("X-XSRF-TOKEN", csrf);

        ResponseEntity<Void> res = rt.postForEntity(base + "/login", new HttpEntity<>(form, headers), Void.class);

        assertThat(cookie(res.getHeaders().get(HttpHeaders.SET_COOKIE), "remember-me")).isNull();
        Integer tokenRows = jdbcTemplate.queryForObject("select count(*) from persistent_logins", Integer.class);
        assertThat(tokenRows).isEqualTo(0);
    }
}
