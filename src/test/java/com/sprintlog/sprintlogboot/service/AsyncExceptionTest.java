package com.sprintlog.sprintlogboot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;

import com.sprintlog.sprintlogboot.exception.NotificationFailedException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("비동기 예외는 어디로 가나")
class AsyncExceptionTest {

  @Autowired
  private NotificationGateway gateway;

  @Autowired
  private NotificationService notifications;

  @BeforeEach
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