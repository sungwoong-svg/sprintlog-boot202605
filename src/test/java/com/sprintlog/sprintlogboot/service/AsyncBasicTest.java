package com.sprintlog.sprintlogboot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("@Async 기본")
class AsyncBasicTest {

  /**
   * 알림 한 건에 걸리는 시간(ms). NotificationGateway 와 맞춰 둔다.
   */
  private static final long SEND_MS = 3_000;

  @Autowired
  private NotificationService notifications;

  @Nested
  @DisplayName("부르는 쪽이 기다리는가")
  class Waiting {

    @Test
    @DisplayName("동기 호출은 끝날 때까지 붙잡힌다")
    void 동기는_기다린다() {
      long took = measure(() -> notifications.sendBlocking("이메일", "목표 달성"));

      assertThat(took).isGreaterThanOrEqualTo(SEND_MS);
    }

    @Test
    @DisplayName("@Async 를 붙이면 부르는 쪽은 바로 돌아온다")
    void 비동기는_바로_돌아온다() {
      long took = measure(() -> notifications.sendAsync("이메일", "목표 달성"));

      assertThat(took).isLessThan(SEND_MS / 3);     // 넉넉히 1초 미만
    }

    @Test
    @DisplayName("결과가 필요하면 CompletableFuture 로 돌려받는다")
    void 결과를_돌려받는다() {
      // ⚠ 호출 '자체' 를 재야 한다. measure 밖에서 부르면 아무것도 검증하지 못한다.
      AtomicReference<CompletableFuture<String>> holder = new AtomicReference<>();
      long took = measure(() -> holder.set(notifications.sendAsyncWithResult("푸시", "목표 달성")));

      assertThat(took).isLessThan(SEND_MS / 3);                 // 맡기고 바로 돌아왔다
      assertThat(holder.get().join()).isEqualTo("푸시 발송 완료");   // 여기서는 기다린다
    }
  }

  @Nested
  @DisplayName("⚠ 비동기가 되지 않는 경우")
  class NotAsync {

    @Test
    @DisplayName("같은 클래스 안에서 부르면(자가 호출) @Async 가 무시된다")
    void 자가_호출은_동기다() {
      long took = measure(() -> notifications.sendViaSelfCall("문자", "목표 달성"));

      // @Async 가 붙어 있는데도 3초를 그대로 기다린다 — 프록시를 거치지 않았기 때문이다.
      assertThat(took).isGreaterThanOrEqualTo(SEND_MS);
    }
  }

  @Nested
  @DisplayName("기본 풀은 어디로 갔나")
  class DefaultPoolIsGone {

    @Autowired
    private ApplicationContext ctx;

    @Test
    @DisplayName("커스텀 Executor가 없을 때 등록되는 기본 풀 정보 확인")
    void checkDefaultExecutorInfo() {
      // 1. 기본 TaskExecutor 빈 조회
      TaskExecutor defaultExecutor = ctx.getBean(TaskExecutor.class);

      // 2. 타입이 ThreadPoolTaskExecutor인지 확인
      assertThat(defaultExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);

      // 3. 내부 ThreadPoolTaskExecutor로 캐스팅하여 풀 메트릭 확인
      ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) defaultExecutor;

      System.out.println("=== Spring Boot 기본 풀 정보 ===");
      System.out.println("Bean Name              : " + pool.getThreadNamePrefix());
      System.out.println("Core Pool Size         : " + pool.getCorePoolSize());
      System.out.println("Max Pool Size          : " + pool.getMaxPoolSize());
      System.out.println("Queue Capacity         : " + pool.getQueueCapacity());
      System.out.println("Keep Alive Seconds     : " + pool.getKeepAliveSeconds());
      System.out.println("Active Threads         : " + pool.getActiveCount());
      System.out.println("Completed Tasks Count  : " + pool.getThreadPoolExecutor().getCompletedTaskCount());

      // 4. Spring Boot 기본 설정값 단언 (application.yml 수정이 없다면)
      // 기본값: Core 8, Max Integer.MAX_VALUE, Queue Integer.MAX_VALUE
      assertThat(pool.getCorePoolSize()).isEqualTo(8);
      assertThat(pool.getQueueCapacity()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Executor 빈을 우리가 만들면 부트의 기본 풀은 물러난다")
    void 기본_풀은_사라졌다() {
      // 지난 시간엔 이 빈이 있었다. 지금은 없다.
      assertThat(ctx.containsBean("applicationTaskExecutor")).isFalse();

      // 대신 우리가 만든 둘이 있다.
      assertThat(ctx.getBeanNamesForType(java.util.concurrent.Executor.class))
          .containsExactlyInAnyOrder("notificationExecutor", "dashboardExecutor");
    }

    @Test
    @DisplayName("이제 일꾼 이름이 task- 가 아니라 noti- 다")
    void 일꾼_이름이_바뀌었다() {
      notifications.sendAsync("이메일", "이름 확인");
      sleepQuietly(300);

      Set<String> workers = Thread.getAllStackTraces().keySet().stream()
          .map(Thread::getName)
          .filter(name -> name.startsWith("noti-") || name.startsWith("task-"))
          .collect(Collectors.toSet());

      System.out.println("  살아 있는 일꾼 = " + workers);
      assertThat(workers).isNotEmpty();
      assertThat(workers).allMatch(name -> name.startsWith("noti-"));   // task- 는 하나도 없다
    }
  }

  private static void sleepQuietly(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

    private static long measure(Runnable work) {
      long start = System.nanoTime();
      work.run();
      return (System.nanoTime() - start) / 1_000_000;
    }

}