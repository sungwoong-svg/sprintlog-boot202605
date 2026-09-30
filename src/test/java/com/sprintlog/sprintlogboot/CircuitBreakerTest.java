package com.sprintlog.sprintlogboot;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintlog.sprintlogboot.client.LectureApiClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 회로 차단기 — Resilience4j 의 @CircuitBreaker.
 *
 * 강의 플랫폼 주소를 아무도 안 듣는 포트(1번) 로 준다 = "상대 서버가 죽어 있다".
 * 그래서 slow-api 를 띄우지 않아도 항상 돈다.
 */
@SpringBootTest(properties = "sprintlog.slow-api.base-url=http://localhost:1")
@ActiveProfiles("test")
@DisplayName("회로 차단기 — 계속 실패하면 아예 안 건다")
class CircuitBreakerTest {

  @Autowired private LectureApiClient lectureApiClient;
  @Autowired private CircuitBreakerRegistry registry;

  @BeforeEach
  void reset() {
    registry.circuitBreaker("lectureApi").reset();   // 테스트마다 닫힌 상태에서 시작
  }

  @Test
  @DisplayName("네 번 실패하면 회로가 열리고, 그다음 호출은 부르지도 않고 곧바로 폴백한다")
  void 열리면_곧바로_폴백() {
    CircuitBreaker cb = registry.circuitBreaker("lectureApi");

    for (int i = 1; i <= 4; i++) {
      Map<String, Object> body = lectureApiClient.fetchLectureGuarded(i, 0, false).block(Duration.ofSeconds(5));
      System.out.println("[관찰] " + i + "번째 호출 → " + body.get("title") + " · 회로 = " + cb.getState());
    }
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    long start = System.nanoTime();
    Map<String, Object> fifth = lectureApiClient.fetchLectureGuarded(5, 0, false).block(Duration.ofSeconds(5));
    long tookMs = (System.nanoTime() - start) / 1_000_000;

    System.out.println("[관찰] 5번째 호출 → " + fifth.get("title") + " · " + tookMs + "ms · 회로 = " + cb.getState()
        + " · 막아 낸 호출 수 = " + cb.getMetrics().getNumberOfNotPermittedCalls());
    assertThat(fifth.get("title")).isEqualTo("(잠시 후 다시 시도해 주세요)");
    assertThat(cb.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
  }

  @Test
  @DisplayName("열리기 전에는 실패가 폴백으로 가도 회로는 닫혀 있다 — 세는 중이다")
  void 열리기_전() {
    CircuitBreaker cb = registry.circuitBreaker("lectureApi");

    lectureApiClient.fetchLectureGuarded(1, 0, false).block(Duration.ofSeconds(5));
    lectureApiClient.fetchLectureGuarded(2, 0, false).block(Duration.ofSeconds(5));

    System.out.println("[관찰] 두 번 실패 뒤 회로 = " + cb.getState()
        + " · 실패 " + cb.getMetrics().getNumberOfFailedCalls() + "건 (최소 4건을 봐야 판단한다)");
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
  }
}
