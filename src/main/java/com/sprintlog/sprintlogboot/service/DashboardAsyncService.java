package com.sprintlog.sprintlogboot.service;

import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardAsyncService {

  private final ActivityDashboard dashboard;

  /**
   * 카테고리별 집계 — 집계 전용 풀(dash-)에서 돈다.
   *
   * 알림이 아무리 밀려 있어도 이 일은 제때 끝난다. 줄이 다르기 때문이다.
   */
  @Async("dashboardExecutor")
  public CompletableFuture<ActivityDashboard.Summary> summarizeAsync() {
    log.info("[집계] 시작 — 일꾼 {}", Thread.currentThread().getName());
    ActivityDashboard.Summary summary = dashboard.summarize();
    log.info("[집계] 완료 — 활동 {}건", summary.getTotalCount());
    return CompletableFuture.completedFuture(summary);
  }

  /**
   * ⚠ 같은 집계인데 알림 풀에 맡긴 것. 풀을 나누기 전의 모습이다.
   *
   * 알림이 밀려 있으면 이 집계도 그 뒤에 줄을 선다 — 집계 코드는 한 줄도 안 바뀌었다.
   */
  @Async("notificationExecutor")
  public CompletableFuture<ActivityDashboard.Summary> summarizeOnNotificationPool() {
    log.info("[집계·알림풀] 시작 — 일꾼 {}", Thread.currentThread().getName());
    return CompletableFuture.completedFuture(dashboard.summarize());
  }

}
