package com.sprintlog.sprintlogboot;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintlog.sprintlogboot.config.RequestContextTaskDecorator;
import com.sprintlog.sprintlogboot.filter.RequestLoggingFilter;
import com.sprintlog.sprintlogboot.service.NotificationService;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스레드를 넘으면 사라지는 것들.
 *
 * 요청 추적 번호(MDC)도 로그인 정보(SecurityContext)도 스레드에 매여 있다.
 * `@Async` 로 넘어가는 순간 사라지는데, `TaskDecorator` 가 그걸 들려 보낸다.
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("스레드를 넘으면 사라지는 것들")
class TaskDecoratorTest {

  @Autowired private NotificationService notifications;

  @AfterEach
  void clearContext() {
    MDC.clear();
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("따라가게 만든다")
  class Propagates {

    @Test
    @DisplayName("요청 추적 번호가 다른 일꾼까지 따라간다")
    void traceId_가_따라간다(CapturedOutput output) {
      // 요청 스레드가 가진 것 둘을 다 심어 둔다 — 실제 요청이 그렇다.
      MDC.put(RequestLoggingFilter.TRACE_ID, "trace777");
      SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken("choon@naver.com", null, List.of()));

      notifications.sendAsyncWithWho("이메일");

      awaitLog(output, "[비동기·누가] 이메일");

      // 일꾼 줄에도 같은 번호가 붙어 있다. TaskDecorator 가 없으면 여기가 [-] 다.
      String workerLine = lineContaining(output, "[비동기·누가] 이메일");
      assertThat(workerLine).contains("trace777");
      assertThat(workerLine).contains("noti-");
    }

    @Test
    @DisplayName("로그인 정보도 따라간다 — 그래서 '누가 요청했는지' 를 남길 수 있다")
    void 로그인_정보가_따라간다(CapturedOutput output) {
      MDC.put(RequestLoggingFilter.TRACE_ID, "trace888");
      SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken("choon@naver.com", null, List.of()));

      notifications.sendAsyncWithWho("푸시");

      awaitLog(output, "[비동기·누가] 푸시");
      String line = lineContaining(output, "[비동기·누가] 푸시");
      assertThat(line).contains("요청자 choon@naver.com");
      assertThat(line).contains("trace888");      // 번호와 요청자가 한 줄에 함께 온다
    }
  }

  @Nested
  @DisplayName("⚠ 따라가면 안 되는 것도 있다")
  class DoesNotPropagate {

    @Test
    @Transactional
    @DisplayName("트랜잭션은 따라가지 않는다 — 그리고 그게 맞다")
    void 트랜잭션은_안_따라간다(CapturedOutput output) {
      // 여기는 트랜잭션 안이다.
      assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
          .isActualTransactionActive()).isTrue();

      notifications.sendAsyncWithWho("문자");

      awaitLog(output, "[비동기·누가] 문자");

      // 그런데 일꾼 쪽은 트랜잭션이 없다.
      //   ⭐ TaskDecorator 로 '전파' 할 수도 있지만 하면 안 된다 —
      //     커넥션 하나를 두 스레드가 나눠 쓰게 되고, 커밋·롤백의 주인이 모호해진다.
      assertThat(lineContaining(output, "[비동기·누가] 문자")).contains("트랜잭션 없음");
    }
  }

  @Nested
  @DisplayName("⚠ 끝나면 지운다")
  class ClearsAfterRun {

    @Autowired private RequestContextTaskDecorator decorator;

    @Test
    @DisplayName("일이 끝나면 일꾼에게서 정보를 지운다 — 안 지우면 다음 일이 남의 번호를 단다")
    void 끝나면_지운다() {
      // ⚠ 풀에 던져서 확인하면 안 된다 — 같은 일꾼이 다시 뽑힌다는 보장이 없어
      //    '지우지 않아도 통과하는' 테스트가 된다. 데코레이터를 **직접** 불러 확인한다.
      AtomicReference<String> seenInsideTask = new AtomicReference<>();

      MDC.put(RequestLoggingFilter.TRACE_ID, "first111");
      Runnable decorated = decorator.decorate(
          () -> seenInsideTask.set(MDC.get(RequestLoggingFilter.TRACE_ID)));

      // 일꾼 스레드에는 원래 아무것도 없다 — 그 상황을 만든다.
      MDC.clear();
      assertThat(MDC.get(RequestLoggingFilter.TRACE_ID)).isNull();

      decorated.run();

      // ① 들고 왔다
      assertThat(seenInsideTask.get()).isEqualTo("first111");
      // ② ⭐ 그리고 끝나면서 지웠다 — 이게 없으면 다음 일이 first111 을 달고 돈다
      assertThat(MDC.get(RequestLoggingFilter.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("로그인 정보도 끝나면 비운다")
    void 로그인_정보도_비운다() {
      SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken("choon@naver.com", null, List.of()));

      AtomicReference<String> seen = new AtomicReference<>();
      Runnable decorated = decorator.decorate(() -> {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        seen.set(auth != null ? auth.getName() : null);
      });

      SecurityContextHolder.clearContext();
      decorated.run();

      assertThat(seen.get()).isEqualTo("choon@naver.com");
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
  }

  // ── 도우미 ────────────────────────────────────────────────
  //  ⚠ 풀 상태(일꾼 수·큐)로 기다리면 안 된다 — 일꾼이 core 보다 적을 때 작업은 큐를 거치지 않아서
  //    '일꾼 0 + 줄 비었음' 이 시작 전에도 참이 된다. 기다림의 기준은 '우리가 볼 줄' 이어야 한다.

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

  private static String lineContaining(CapturedOutput output, String needle) {
    return output.toString().lines()
        .filter(line -> line.contains(needle))
        .findFirst()
        .orElseThrow(() -> new AssertionError("그런 줄이 없다: " + needle));
  }
}