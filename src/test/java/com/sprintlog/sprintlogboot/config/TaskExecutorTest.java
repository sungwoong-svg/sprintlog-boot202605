package com.sprintlog.sprintlogboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintlog.sprintlogboot.service.ActivityDashboard;
import com.sprintlog.sprintlogboot.service.DashboardAsyncService;
import com.sprintlog.sprintlogboot.service.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

/**
 * 풀을 나누면 무엇이 달라지는가.
 *
 * 지난 시간의 기본 풀은 '일꾼 8명 + 줄 무제한' 하나뿐이었다.
 * 그래서 알림이 밀리면 **대시보드 집계도 같은 줄 뒤에** 선다.
 *
 * 오늘은 풀이 둘이다 — 알림용(noti-)과 집계용(dash-).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("풀을 나눈다")
class TaskExecutorTest {

  /** 알림 한 건에 걸리는 시간(ms). NotificationGateway 와 맞춰 둔다. */
  private static final long SEND_MS = 3_000;

  /**
   * 알림 풀이 한 번에 받아 줄 수 있는 건수.
   *
   * ⚠ 이 값은 **고정이 아니다.** 풀이 비어 있을 때는 일꾼 4명 + 줄 4칸 = 8건까지 받지만,
   *    이미 일꾼 4명이 다 뽑혀 있으면(앞에서 한 번 바빴던 경우) **줄 4칸이 전부**다.
   *    일꾼을 새로 뽑을 여지가 없기 때문이다.
   *    그래서 테스트는 '정확히 몇 건' 이 아니라 **범위**로 단언한다.
   */
  private static final int MIN_ACCEPTED = 4;     // 줄 4칸은 언제나 받는다
  private static final int MAX_ACCEPTED = 8;     // 풀이 비어 있었다면 일꾼 4명까지 더

  @Autowired private NotificationService notifications;
  @Autowired private DashboardAsyncService dashboardAsync;
  @Autowired private ThreadPoolTaskExecutor notificationExecutor;

  @Nested
  @DisplayName("우리가 만든 풀")
  class OurPools {

    @Autowired private ThreadPoolTaskExecutor notificationExecutor;
    @Autowired private ThreadPoolTaskExecutor dashboardExecutor;

    @Test
    @DisplayName("설정한 값이 그대로 들어가 있다")
    void 설정값을_확인한다() {
      System.out.println("  notificationExecutor  core=" + notificationExecutor.getCorePoolSize()
          + " max=" + notificationExecutor.getMaxPoolSize()
          + " queue=" + notificationExecutor.getQueueCapacity()
          + " prefix=" + notificationExecutor.getThreadNamePrefix());
      System.out.println("  dashboardExecutor     core=" + dashboardExecutor.getCorePoolSize()
          + " max=" + dashboardExecutor.getMaxPoolSize()
          + " queue=" + dashboardExecutor.getQueueCapacity()
          + " prefix=" + dashboardExecutor.getThreadNamePrefix());

      // ⭐ 기본 풀과 가장 다른 점 — 줄에 상한이 있다(기본 풀은 21억이었다).
      assertThat(notificationExecutor.getQueueCapacity()).isEqualTo(4);
      assertThat(notificationExecutor.getMaxPoolSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("각자 다른 일꾼이 일한다 — noti- 와 dash-")
    void 일꾼_이름이_다르다() {
      CompletableFuture<String> noti = notifications.sendAsyncWithResult("이메일", "이름 확인");
      CompletableFuture<ActivityDashboard.Summary> dash = dashboardAsync.summarizeAsync();

      noti.join();
      dash.join();

      // 로그의 일꾼 이름을 눈으로도 확인할 것 — noti-1 / dash-1
      assertThat(notificationExecutor.getThreadNamePrefix()).isEqualTo("noti-");
      assertThat(dashboardExecutor.getThreadNamePrefix()).isEqualTo("dash-");
    }
  }

  @Nested
  @DisplayName("⭐ 한쪽이 막혀도 다른 쪽은 돈다")
  class Isolation {

    @Test
    @DisplayName("알림이 폭주하는 중에도 집계는 제때 끝난다")
    void 집계는_알림에_막히지_않는다() throws Exception {
      // 알림 일꾼들을 전부 3초짜리 일로 묶어 둔다(거절이 나지 않는 범위에서).
      List<CompletableFuture<String>> flood = flood(MIN_ACCEPTED);

      // 그 상태에서 집계를 부른다.
      long start = System.nanoTime();
      ActivityDashboard.Summary summary = dashboardAsync.summarizeAsync().get();
      long took = (System.nanoTime() - start) / 1_000_000;

      System.out.println("  알림 " + MIN_ACCEPTED + "건이 밀려 있는 중 집계 소요 = " + took + "ms");

      // 풀이 하나였다면 알림 뒤에 줄을 서서 최소 3초를 기다렸을 것이다.
      assertThat(took).isLessThan(SEND_MS);
      assertThat(summary).isNotNull();

      flood.forEach(CompletableFuture::join);
    }

    @Test
    @DisplayName("⚠ 같은 집계라도 알림 풀에 맡기면 알림 뒤에 줄을 선다")
    void 같은_풀이면_밀린다() throws Exception {
      // 풀을 나누기 전의 모습 — 집계 코드는 동일하고, '어느 풀인가' 만 다르다.
      List<CompletableFuture<String>> flood = flood(MIN_ACCEPTED);

      long start = System.nanoTime();
      dashboardAsync.summarizeOnNotificationPool().get();
      long took = (System.nanoTime() - start) / 1_000_000;

      System.out.println("  같은 풀에 맡긴 집계 소요 = " + took + "ms");

      // 알림 한 건이 끝나야 일꾼이 난다.
      // ⚠ 정확히 3초는 아니다 — flood() 가 일꾼들이 일을 잡도록 잠깐 기다린 뒤에 재기 시작하므로
      //    그 시간만큼 빠진다(실측 2.81초). 중요한 건 '앞의 알림이 끝나기를 기다린다' 는 사실이다.
      assertThat(took).isGreaterThan(SEND_MS * 3 / 4);

      flood.forEach(CompletableFuture::join);
    }
  }

  @Nested
  @DisplayName("줄이 차면 거절한다")
  class Saturation {

    @Autowired private MeterRegistry registry;

    @Test
    @DisplayName("담을 수 있는 만큼만 받고 나머지는 TaskRejectedException")
    void 넘치면_거절한다() {
      awaitIdle();

      List<CompletableFuture<String>> accepted = new ArrayList<>();
      int rejected = 0;

      for (int i = 1; i <= 20; i++) {
        try {
          accepted.add(notifications.sendAsyncWithResult("채널" + i, "폭주"));
        } catch (TaskRejectedException e) {
          rejected++;
        }
      }

      System.out.println("  20건 제출 → 수락 " + accepted.size() + " · 거절 " + rejected);

      // ⭐ 핵심은 '몇 건' 이 아니라 **받지 못한 일이 그 자리에서 거절됐다**는 것이다.
      //    기본 풀이었다면 20건 전부 줄에 쌓였다(그리고 아무 티도 안 났다).
      assertThat(accepted.size()).isBetween(MIN_ACCEPTED, MAX_ACCEPTED);
      assertThat(rejected).isEqualTo(20 - accepted.size());
      assertThat(rejected).isPositive();

      accepted.forEach(CompletableFuture::join);
    }

    @Test
    @DisplayName("Actuator 지표로 포화를 미리 본다 — queue.remaining 이 0 으로 간다")
    void 포화를_지표로_본다() {
      awaitIdle();

      double before = gauge("executor.queue.remaining");
      System.out.println("  평소  executor.queue.remaining = " + before);
      assertThat(before).isEqualTo(4.0);          // 텅 빈 상태

      // 거절이 시작될 때까지 던진다 — 그 순간의 지표를 보는 게 목적이다.
      List<CompletableFuture<String>> accepted = new ArrayList<>();
      for (int i = 1; i <= 20; i++) {
        try {
          accepted.add(notifications.sendAsyncWithResult("채널" + i, "폭주"));
        } catch (TaskRejectedException e) {
          break;
        }
      }

      double during = gauge("executor.queue.remaining");
      double active = gauge("executor.active");
      System.out.println("  포화  executor.queue.remaining = " + during + " · executor.active = " + active);

      // ⭐ 줄이 0칸 남았다 = 다음 요청부터 거절이다. 이게 '미리 보는' 신호다.
      assertThat(during).isZero();
      assertThat(active).isGreaterThan(0);

      accepted.forEach(CompletableFuture::join);
    }

    private double gauge(String name) {
      return registry.get(name).tag("name", "notificationExecutor").gauge().value();
    }
  }

  /** 알림 풀을 가득 채운다. 거절이 나기 직전까지만 던진다. */
  private List<CompletableFuture<String>> flood(int count) {
    // ⚠ 앞 테스트가 남긴 일이 아직 돌고 있으면 여기서 거절이 나 버린다.
    //    테스트끼리 간섭하지 않도록, 풀이 빌 때까지 기다린 뒤에 던진다.
    awaitIdle();

    List<CompletableFuture<String>> calls = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      calls.add(notifications.sendAsyncWithResult("채널" + i, "폭주"));
    }
    sleepQuietly(200);   // 일꾼들이 실제로 일을 잡을 시간을 준다
    return calls;
  }

  /** 알림 풀이 완전히 비는 것을 기다린다(일꾼 0명 · 줄 0칸). */
  private void awaitIdle() {
    long deadline = System.nanoTime() + 15_000_000_000L;
    while (System.nanoTime() < deadline) {
      boolean idle = notificationExecutor.getActiveCount() == 0
          && notificationExecutor.getThreadPoolExecutor().getQueue().isEmpty();
      if (idle) {
        return;
      }
      sleepQuietly(50);
    }
    throw new IllegalStateException("알림 풀이 15초 안에 비지 않았다 — 앞 테스트가 일을 남겼다");
  }

  private static void sleepQuietly(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}