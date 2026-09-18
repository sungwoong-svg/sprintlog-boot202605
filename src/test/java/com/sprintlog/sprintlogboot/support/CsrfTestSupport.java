package com.sprintlog.sprintlogboot.support;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * [CP127c] CSRF 를 켠 뒤, 통합 테스트(@SpringBootTest)에서 상태변경 요청에 CSRF 토큰을 실어 보내기 위한 헬퍼.
 *
 * SPA 실제 흐름 그대로 재현한다:
 *   1) GET /api/v1/auth/csrf-token → 응답 Set-Cookie 로 XSRF-TOKEN(raw) 발급.
 *   2) 이후 상태변경(POST/PUT/DELETE) 요청에 Cookie: XSRF-TOKEN=<t> + Header: X-XSRF-TOKEN=<t> 를 함께 실어 보낸다.
 *      (CookieCsrfTokenRepository 는 쿠키에서 기대값을, SpaCsrfTokenRequestHandler 는 헤더에서 제출값을 읽어 대조한다.)
 */
public final class CsrfTestSupport {

    public static final String COOKIE_NAME = "XSRF-TOKEN";
    public static final String HEADER_NAME = "X-XSRF-TOKEN";

    private CsrfTestSupport() {}

    /** Set-Cookie 목록에서 name 쿠키의 값을 추출(없으면 null). */
    public static String cookieValue(HttpHeaders headers, String name) {
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) return null;
        for (String c : setCookies) {
            String first = c.split(";", 2)[0];               // "XSRF-TOKEN=abc; Path=/" → "XSRF-TOKEN=abc"
            if (first.startsWith(name + "=")) return first.substring(name.length() + 1);
        }
        return null;
    }

    /** GET /api/v1/auth/csrf-token 을 호출해 XSRF-TOKEN(raw) 을 발급받아 반환. */
    public static String fetchToken(RestTemplate rt, String base) {
        ResponseEntity<Void> r = rt.getForEntity(base + "/api/v1/auth/csrf-token", Void.class);
        return cookieValue(r.getHeaders(), COOKIE_NAME);
    }
}
