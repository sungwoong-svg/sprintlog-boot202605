package com.sprintlog.sprintlogboot;

import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.domain.Role;
import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.domain.Visibility;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [CP121] 인가 프로세스 통합 테스트 — 소유권 기반 인가(@PreAuthorize) + 401/403 구분.
 *   활동 삭제는 '소유자 본인 또는 ADMIN' 만 가능하다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("인가 프로세스 통합 테스트 (소유권 @PreAuthorize + 401/403)")
class AuthorizationProcessTest {

    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    UserRepository userRepository;
    @Autowired
    ActivityRepository activityRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    String base;
    Long activityId;

    @BeforeEach
    void setUp() {
        activityRepository.deleteAll();
        userRepository.deleteAll();

        User choon = userRepository.save(new User("김춘식", "choon@naver.com", passwordEncoder.encode("password123")));       // ROLE_USER
        userRepository.save(new User("홍길동", "hong@gmail.com", passwordEncoder.encode("password123")));                    // ROLE_USER (타인)
        userRepository.save(new User("관리자", "admin@sprintlog.com", passwordEncoder.encode("admin123"), Role.ADMIN));       // ROLE_ADMIN

        // choon 이 소유한 활동을 하나 심는다.
        LearningActivity activity = new LearningActivity(
                ActivityCategory.LECTURE, "choon 의 활동", 30, Visibility.PUBLIC, "이강사", null, null);
        activity.assignOwner(choon);
        activityId = activityRepository.save(activity).getId();

        base = "http://localhost:" + port + "/api/v1/activities/" + activityId;
    }

    /** [CP127c] CSRF 토큰을 발급받아 쿠키+헤더로 담은 (본문 없는) HttpEntity 를 만든다. DELETE 등 상태변경에 쓴다. */
    private org.springframework.http.HttpEntity<Void> csrfEntity() {
        String host = "http://localhost:" + port;
        ResponseEntity<Void> r = rest.getForEntity(host + "/api/v1/auth/csrf-token", Void.class);
        String csrf = com.sprintlog.sprintlogboot.support.CsrfTestSupport.cookieValue(r.getHeaders(), "XSRF-TOKEN");
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.add(org.springframework.http.HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        h.add("X-XSRF-TOKEN", csrf);
        return new org.springframework.http.HttpEntity<>(h);
    }

    @Test
    @DisplayName("소유자 본인 → 자기 활동 삭제 허용(204)")
    void 소유자_삭제_허용() {
        ResponseEntity<Void> res = rest.withBasicAuth("choon@naver.com", "password123")
                .exchange(base, HttpMethod.DELETE, csrfEntity(), Void.class);   // [CP127c] CSRF 토큰 동반
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("다른 사용자 → 남의 활동 삭제 거부(403, 인증됐지만 소유자 아님)")
    void 타인_삭제_거부_403() {
        ResponseEntity<String> res = rest.withBasicAuth("hong@gmail.com", "password123")
                .exchange(base, HttpMethod.DELETE, csrfEntity(), String.class);   // [CP127c] 토큰 동반 → 403 이 'CSRF' 가 아니라 '소유권' 때문임을 보장
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // 남의 활동은 그대로 남아 있어야 한다(삭제 안 됨).
        assertThat(activityRepository.findById(activityId)).isPresent();
    }

    @Test
    @DisplayName("ADMIN → 남의 활동도 삭제 허용(204, 역할 우선)")
    void 관리자_삭제_허용() {
        ResponseEntity<Void> res = rest.withBasicAuth("admin@sprintlog.com", "admin123")
                .exchange(base, HttpMethod.DELETE, csrfEntity(), Void.class);   // [CP127c] CSRF 토큰 동반
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("자격증명 없음 → 삭제 401(인증 필요)")
    void 미인증_삭제_401() {
        // [CP127c] CSRF 토큰은 실어 보내되(토큰은 '자격증명' 이 아니다) Basic 인증만 뺀다 →
        //   CSRF 는 통과하고 인가 단계에서 '미인증' 으로 순수하게 401 이 나는지 검증한다.
        ResponseEntity<String> res = rest.exchange(base, HttpMethod.DELETE, csrfEntity(), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(activityRepository.findById(activityId)).isPresent();
    }
}
