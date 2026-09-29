package com.sprintlog.sprintlogboot.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.domain.Visibility;
import com.sprintlog.sprintlogboot.domain.WeeklyGoal;
import com.sprintlog.sprintlogboot.dto.request.CreateActivityRequest;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import com.sprintlog.sprintlogboot.repository.AuditLogRepository;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import com.sprintlog.sprintlogboot.repository.WeeklyGoalRepository;
import com.sprintlog.sprintlogboot.service.ActivityDashboard;
import com.sprintlog.sprintlogboot.service.ActivityService;
import com.sprintlog.sprintlogboot.service.NotificationService;
import java.time.LocalDate;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 활동 등록 → 이벤트 → 알림.
 *
 * 오늘의 질문은 하나다 — **알림은 언제 나가야 하는가?**
 * 저장이 확정되기 전에 나가면, 롤백됐을 때 "기록됐습니다" 라는 거짓말이 남는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("이벤트로 알림 붙이기")
class ActivityEventTest {

  /** 알림이 실제로 나갔는지만 보면 되므로 가짜로 바꾼다(3초를 기다릴 이유가 없다). */
  @MockitoBean
  private NotificationService notifications;

  @Autowired private ActivityService service;
  @Autowired private ActivityRepository activityRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private WeeklyGoalRepository goalRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private ActivityDashboard dashboard;
  @Autowired private TransactionTemplate tx;

  private User owner;

  @Autowired private org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor notificationExecutor;

  @BeforeEach
  void setUp() {
    // ⚠ 앞 테스트가 던진 @Async 알림이 아직 돌고 있을 수 있다.
    //    그대로 두면 늦게 도착한 호출이 '다음 테스트의 새 가짜 객체' 에 꽂혀 엉뚱한 실패가 난다.
    //    (@MockitoBean 은 테스트마다 리셋되는데, 비동기 일은 아무도 기다려 주지 않기 때문이다.)
    awaitNotificationIdle();

    activityRepository.deleteAll();
    goalRepository.deleteAll();
    auditLogRepository.deleteAll();
    userRepository.deleteAll();

    owner = userRepository.save(new User("김춘식", "choon@naver.com", "hashed"));
  }

  /**
   * 이번 주 목표 60분을 심는다. 활동 하나(90분)면 바로 달성된다.
   *
   * ⚠ 모든 테스트에 심으면 안 된다 — 목표가 있으면 활동을 등록할 때마다 알림이 나가고,
   *   그 알림은 @Async 라 아무도 기다려 주지 않아서 **다음 테스트로 새어 들어간다.**
   *   필요한 테스트에서만 부른다.
   */
  private void 주간목표_60분을_정한다() {
    WeeklyGoal goal = new WeeklyGoal(60);
    goal.assignUser(owner);
    goalRepository.save(goal);
  }

  @Nested
  @DisplayName("먼저, 그동안 버려지던 값들")
  class MissingPieces {

    @Test
    @DisplayName("요청의 studiedOn 이 이제 저장되고 응답에도 나온다")
    void studiedOn_이_살아남는다() {
      LocalDate 어제 = LocalDate.now().minusDays(1);

      LearningActivity saved = service.create(request("어제 공부한 것", 30, 어제), null, owner.getEmail());

      // 그동안은 여기가 null 이었다 — 요청에 담아 보내도 엔티티에 자리가 없었으니까.
      assertThat(saved.getStudiedOn()).isEqualTo(어제);
      assertThat(activityRepository.findById(saved.getId()).orElseThrow().getStudiedOn()).isEqualTo(어제);
    }

    @Test
    @DisplayName("이번 주 학습 시간을 '그 사람 것만' 센다")
    void 사용자별로_센다() {
      User 남 = userRepository.save(new User("홍길동", "hong@gmail.com", "hashed"));
      LocalDate 오늘 = LocalDate.now();

      service.create(request("내 공부", 40, 오늘), null, owner.getEmail());
      service.create(request("남의 공부", 100, 오늘), null, 남.getEmail());

      // findAll() 로 셌다면 140 이 나왔을 것이다.
      assertThat(dashboard.weeklyStudiedMinutes(owner.getId(), 오늘)).isEqualTo(40);
      assertThat(dashboard.weeklyStudiedMinutes(남.getId(), 오늘)).isEqualTo(100);
    }

    @Test
    @DisplayName("주간 목표가 DB 에 남는다 — 화면이 매번 들고 오지 않아도 된다")
    void 목표가_저장된다() {
      주간목표_60분을_정한다();

      WeeklyGoal found = goalRepository.findByUserId(owner.getId()).orElseThrow();

      assertThat(found.getTargetMinutes()).isEqualTo(60);
      assertThat(found.isAchieved(90)).isTrue();
    }
  }

  @Nested
  @DisplayName("⭐ 알림은 언제 나가는가")
  class WhenIsItSent {

    @Test
    @DisplayName("커밋이 끝나야 알림이 나간다")
    void 커밋되면_나간다() {
      주간목표_60분을_정한다();

      service.create(request("긴 공부", 90, LocalDate.now()), null, owner.getEmail());

      // @Async 라 다른 일꾼에서 돈다 → 잠깐 기다려 준다.
      verify(notifications, timeout(3_000)).sendAsync(eq("푸시"), contains("목표 달성"));
    }

    @Test
    @DisplayName("⚠ 저장이 롤백되면 알림은 나가지 않는다")
    void 롤백되면_안_나간다() {
      주간목표_60분을_정한다();

      assertThatThrownBy(() ->
          tx.executeWithoutResult(status -> {
            service.create(request("취소될 공부", 90, LocalDate.now()), null, owner.getEmail());
            throw new IllegalStateException("저장 뒤에 터진 문제");
          }))
          .isInstanceOf(IllegalStateException.class);

      // 활동도 남지 않았고,
      assertThat(activityRepository.count()).isZero();
      // "기록됐습니다" 라는 거짓말도 나가지 않았다.
      verify(notifications, after(1_500).never()).sendAsync(any(), any());
    }

    @Test
    @DisplayName("반면 '시도했다' 는 기록은 롤백돼도 남는다 — 별도 트랜잭션이라서")
    void 감사기록은_남는다() {
      assertThatThrownBy(() ->
          tx.executeWithoutResult(status -> {
            service.create(request("취소될 공부", 90, LocalDate.now()), null, owner.getEmail());
            throw new IllegalStateException("저장 뒤에 터진 문제");
          }))
          .isInstanceOf(IllegalStateException.class);

      assertThat(activityRepository.count()).isZero();
      assertThat(auditLogRepository.count()).isPositive();   // REQUIRES_NEW 로 따로 커밋됐다
    }
  }

  @Nested
  @DisplayName("조건을 붙여 골라 듣기")
  class Conditional {

    @Test
    @DisplayName("condition 식이 참일 때만 불린다 — 120분 이상만 축하한다")
    void 조건부_리스너(CapturedOutput output) {
      service.create(request("짧은 공부", 90, LocalDate.now()), null, owner.getEmail());
      assertThat(output).doesNotContain("[이벤트·조건]");

      service.create(request("긴 공부", 150, LocalDate.now()), null, owner.getEmail());
      assertThat(output).contains("[이벤트·조건]").contains("150분");
    }
  }

  /** 알림 풀이 빌 때까지 기다린다 — 테스트끼리 간섭하지 않도록. */
  private void awaitNotificationIdle() {
    long deadline = System.nanoTime() + 10_000_000_000L;
    while (System.nanoTime() < deadline) {
      boolean idle = notificationExecutor.getActiveCount() == 0
          && notificationExecutor.getThreadPoolExecutor().getQueue().isEmpty();
      if (idle) {
        return;
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private CreateActivityRequest request(String title, int minutes, LocalDate studiedOn) {
    // 인자 순서 주의 — studiedOn 은 tags 다음(6번째)이다.
    return new CreateActivityRequest(
        ActivityCategory.LECTURE, title, minutes, Visibility.PUBLIC,
        null, studiedOn, "이강사", null, null);
  }
}