package com.sprintlog.sprintlogboot;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintlog.sprintlogboot.client.LectureApiClient;
import com.sprintlog.sprintlogboot.client.NotificationApiClient;
import com.sprintlog.sprintlogboot.filter.RequestLoggingFilter;
import com.sprintlog.sprintlogboot.service.NotificationService;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("slowApiIsUp")
@DisplayName("진짜 바깥 서버를 부른다 (slow-api 필요)")
class SlowApiLiveTest {

  @Autowired private LectureApiClient lectureApiClient;
  @Autowired private NotificationApiClient notificationApiClient;
  @Autowired private NotificationService notificationService;

  /** 8081 에 누가 듣고 있는가 — 없으면 이 클래스 전체를 건너뛴다. */
  static boolean slowApiIsUp() {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("localhost", 8081), 300);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @AfterEach
  void clear() {
    MDC.clear();
  }

  @Test
  @DisplayName("빠른 응답 — 상대가 0.5초면 우리도 0.5초대")
  void 빠른_응답() {
    MDC.put(RequestLoggingFilter.TRACE_ID, "live0001");

    long startedAt = System.currentTimeMillis();
    Map<String, Object> body = lectureApiClient.fetchLecture(1, 500).block(Duration.ofSeconds(30));
    long tookMs = System.currentTimeMillis() - startedAt;

    System.out.println("[측정] 빠른 응답 = " + tookMs + "ms → " + body);
    assertThat(body).containsKey("title");
    assertThat(body.get("title")).isNotEqualTo("(불러오지 못함)");
  }

  @Test
  @DisplayName("⚠ 시한 초과 + 재시도 — 2초 시한인데 훨씬 오래 걸린다")
  void 시한_초과와_재시도() {
    MDC.put(RequestLoggingFilter.TRACE_ID, "live0002");

    long startedAt = System.currentTimeMillis();
    Map<String, Object> body = lectureApiClient.fetchLecture(2, 3000).block(Duration.ofSeconds(60));
    long tookMs = System.currentTimeMillis() - startedAt;

    System.out.println("[측정] 시한 초과 + 재시도 = " + tookMs + "ms → " + body);

    // 폴백으로 내려왔고
    assertThat(body).containsEntry("title", "(불러오지 못함)");
    // ⭐ 시한 2초보다 훨씬 오래 걸렸다 — 재시도는 실패를 더 오래 끈다
    assertThat(tookMs).isGreaterThan(6_000L);
  }

  @Test
  @DisplayName("⭐ 보내는 호출 — 직접 부르면 기다리고, 맡기면 바로 돌아온다")
  void 보내는_호출과_맡기기() throws Exception {
    MDC.put(RequestLoggingFilter.TRACE_ID, "live0004");

    // ① 직접 부르면 응답이 올 때까지 여기서 기다린다.
    long startedAt = System.currentTimeMillis();
    Map<String, Object> sent = notificationApiClient.send("이메일", "주간 목표 달성", 500)
        .block(Duration.ofSeconds(30));
    long directMs = System.currentTimeMillis() - startedAt;
    System.out.println("[측정] POST 를 직접 부르면 = " + directMs + "ms → " + sent);

    // ② @Async 에 맡기면 부른 쪽은 즉시 돌아온다 — 발송은 아직 안 끝났는데도.
    startedAt = System.currentTimeMillis();
    notificationService.sendAsyncViaHttp("이메일", "주간 목표 달성", 1000);
    long handOffMs = System.currentTimeMillis() - startedAt;
    System.out.println("[측정] sendAsyncViaHttp 를 부르면 = " + handOffMs + "ms 에 돌아온다");

    assertThat(sent).containsEntry("status", "발송 완료");
    // ⭐ 맡기는 건 부르는 것보다 압도적으로 빨리 끝난다
    assertThat(handOffMs).isLessThan(directMs);

    Thread.sleep(1500);   // 뒷정리 — 발송이 끝나는 것까지 보고 나간다
  }

  @Test
  @DisplayName("여러 건을 한꺼번에 — 5건 × 1초가 1초대")
  void 동시_호출() {
    MDC.put(RequestLoggingFilter.TRACE_ID, "live0003");

    long startedAt = System.currentTimeMillis();
    List<Map<String, Object>> all =
        lectureApiClient.fetchAll(List.of(1L, 2L, 3L, 4L, 5L), 1000).block(Duration.ofSeconds(60));
    long tookMs = System.currentTimeMillis() - startedAt;

    System.out.println("[측정] 5건 동시 호출 = " + tookMs + "ms → " + all.size() + "건");
    assertThat(all).hasSize(5);
    // 하나씩 불렀다면 5초였다.
    // ⚠ 여유를 넉넉히 둔다 — 기계가 바쁘면 건당 2초 시한에 걸려 재시도로 빠질 수 있다.
    assertThat(tookMs).isLessThan(4_500L);
  }
}
