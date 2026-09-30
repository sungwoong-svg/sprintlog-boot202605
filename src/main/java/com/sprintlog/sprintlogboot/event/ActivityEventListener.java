package com.sprintlog.sprintlogboot.event;

import com.sprintlog.sprintlogboot.domain.WeeklyGoal;
import com.sprintlog.sprintlogboot.repository.WeeklyGoalRepository;
import com.sprintlog.sprintlogboot.service.ActivityDashboard;
import com.sprintlog.sprintlogboot.service.AuditService;
import com.sprintlog.sprintlogboot.service.NotificationService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class ActivityEventListener {

  private final AuditService auditService;
  private final NotificationService notifications;
  private final ActivityDashboard dashboard;
  private final WeeklyGoalRepository goalRepository;

  @EventListener
  public void onCreatedForAudit(ActivityCreatedEvent event) {
    log.info("[이벤트, 동기] 감사 기록 - 일꾼 {}", Thread.currentThread().getName());
    auditService.logAttempt("CREATE", "활동 등록: " + event.title());
  }


  // 저장이 성공한 것이 확정된 뒤에만 이벤트가 동작
  // 커밋이 끝난 뒤 다른 스레드로 이벤트를 동작.
  // 이 메서드 안에서 디비에 쓰기 작업하면 에러 없이 저장이 안됨
  // 원래 트랜잭션이 커밋이 끝나고 난 후에 동작하기 때문에 꼭 써야한다면 REQUIRES_NEW로 새 트랙잭션을 열어야 합니다.(읽기는 괜찮)
  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCreatedForNotification(ActivityCreatedEvent event) {
    log.info("[이벤트·커밋후] 알림 판단 — 일꾼 {}", Thread.currentThread().getName());
    notifications.sendAsyncWithWho("푸시");

    Optional<WeeklyGoal> goal = goalRepository.findByUserId(event.ownerId());
    if (goal.isEmpty()) {
      log.info("[이벤트·커밋후] 주간 목표가 없어 알림을 보내지 않는다 — 사용자 {}", event.ownerId());
      return;
    }

    int studied = dashboard.weeklyStudiedMinutes(event.ownerId(), event.studiedOn());
    if (goal.get().isAchieved(studied)) {
      notifications.sendAsync("푸시", "이번 주 목표 달성! (" + studied + "분)");
    } else {
      log.info("[이벤트·커밋후] 아직 {}분 남았다", goal.get().remainingMinutes(studied));
    }
  }

  // 조건부 이벤트 구독
  @TransactionalEventListener(
      phase = TransactionPhase.AFTER_COMMIT,
      condition = "#event.minutes() >= 120")
  public void onLongSession(ActivityCreatedEvent event) {
    log.info("[이벤트·조건] 두 시간 넘게 공부했다 — {}분", event.minutes());
  }

  // 평소엔 불리지 않는다. 롤백 상황일 때만 로그 한 줄이 상황을 설명해 준다.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
  public void onRolledBack(ActivityCreatedEvent event) {
    log.warn("[이벤트·롤백] 활동 등록이 취소됐다 — 알림을 보내지 않는다. 제목={}", event.title());
  }

}
