package com.sprintlog.sprintlogboot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintlog.sprintlogboot.exception.NotificationFailedException;
import com.sprintlog.sprintlogboot.service.NotificationGateway;
import com.sprintlog.sprintlogboot.service.NotificationService;
import com.sprintlog.sprintlogboot.service.ReliableNotificationSender;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

/**
 * 비동기로 던져진 예외는 어디로 가는가.
 *
 * 지난 시간 마지막 질문이 이거였다 — "알림 리스너가 실패하면 누가 아나요?"
 * 답은 반환 타입에 따라 다르다.
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("비동기 예외는 어디로 가나")
class AsyncExceptionTest {

  @Autowired
  private NotificationService notifications;
  @Autowired private ReliableNotificationSender reliable;
  @Autowired private NotificationGateway gateway;
  @Autowired private ThreadPoolTaskExecutor notificationExecutor;

  @org.junit.jupiter.api.BeforeEach
  void resetGateway() {
    gateway.simulateFailures(0);   // 앞 테스트가 남긴 고장 설정을 지운다
  }

  private static void sleepQuietly(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Nested
  @DisplayName("반환 타입에 따라 갈 곳이 다르다")
  class ByReturnType {

    @Test
    @DisplayName("void 는 부르는 쪽이 모른다 — 핸들러가 마지막으로 받는다")
    void void_는_핸들러로_간다(CapturedOutput output) {
      // 부르는 쪽은 아무 일도 없었던 것처럼 지나간다.
      assertThatNoException().isThrownBy(() -> notifications.sendAsyncThatFails("이메일", "실패 관찰"));

      // 그런데 사라지지는 않았다 — 우리가 만든 핸들러가 받아 뒀다.
      awaitLog(output, "[비동기 예외] 알림 실패");
      assertThat(output).contains("[비동기 예외] 알림 실패");
      assertThat(output).contains("sendAsyncThatFails");
    }

    @Test
    @DisplayName("CompletableFuture 는 표 안에 담겨 온다 — 핸들러로 가지 않는다")
    void future_는_표에_담겨_온다(CapturedOutput output) {
      CompletableFuture<String> future = notifications.sendAsyncWithResultThatFails("푸시", "실패 관찰");

      // 펼쳐 보면 그제야 터진다(join 이 끝날 때까지 기다려 준다). 원인은 감싸여 있다.
      assertThatThrownBy(future::join)
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(NotificationFailedException.class);

      // 표는 '실패 상태' 로 완료돼 있다.
      assertThat(future.isCompletedExceptionally()).isTrue();

      // ⭐ 핸들러는 이 예외를 보지 못했다 — 부르는 쪽에 이미 건넸기 때문이다.
      //   (핸들러가 잡았다면 '[비동기 예외]' 로 시작하는 줄이 찍혔을 것이다.)
      sleepQuietly(300);
      assertThat(output).doesNotContain("[비동기 예외]");
    }

    @Test
    @DisplayName("예외 종류에 따라 로그 급이 다르다")
    void 종류별로_다르게_다룬다(CapturedOutput output) {
      notifications.sendAsyncThatFails("문자", "알려진 실패");

      // 알림 실패는 '다시 걸어 볼 만한' 것이라 WARN 급으로 남긴다.
      awaitLog(output, "다시 걸어 볼 만하다");
      assertThat(output).contains("다시 걸어 볼 만하다");
    }
  }

  @Nested
  @DisplayName("알기만 하면 부족하다 — 다시 걸어 보고, 다른 길로 간다")
  class RetryAndFallback {

    @Test
    @DisplayName("두 번 실패해도 세 번째에 성공하면 알림은 나간다")
    void 재시도로_살아난다(CapturedOutput output) {
      gateway.simulateFailures(2);          // 앞의 두 번만 실패

      reliable.sendWithRetry("이메일", "재시도 관찰");
      awaitLog(output, "3번째 시도에서 성공");

      assertThat(output).contains("1/3 실패").contains("2/3 실패");
      assertThat(output).contains("3번째 시도에서 성공");
      assertThat(output).doesNotContain("[폴백]");
    }

    @Test
    @DisplayName("세 번 다 실패하면 포기하되, 실패했다는 사실은 남긴다")
    void 폴백으로_흔적을_남긴다(CapturedOutput output) {
      gateway.simulateFailures(3);          // 세 번 다 실패

      reliable.sendWithRetry("이메일", "폴백 관찰");
      awaitLog(output, "[폴백]");

      assertThat(output).contains("3/3 실패");
      assertThat(output).contains("[폴백]").contains("나중에 다시 보낼 목록");
    }
  }

  /**
   * 기다리는 기준을 '풀이 비었나'가 아니라 '우리가 볼 줄이 찍혔나' 로 잡는다.
   *
   * ⚠ 풀 상태로 기다리면 안 된다. 일꾼이 core 보다 적을 때 작업은 큐를 거치지 않고
   *   새 일꾼에게 바로 넘어가는데, 그 찰나에 "일하는 일꾼 0명 · 줄 비어 있음" 이 동시에 참이 된다.
   *   그래서 아직 시작도 안 한 일을 두고 '끝났다' 고 판단해 버린다.
   */
  private static void awaitLog(CapturedOutput output, String needle) {
    long deadline = System.nanoTime() + 15_000_000_000L;
    while (System.nanoTime() < deadline) {
      if (output.toString().contains(needle)) {
        return;
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    throw new AssertionError("15초 안에 로그가 나타나지 않았다: " + needle);
  }
}