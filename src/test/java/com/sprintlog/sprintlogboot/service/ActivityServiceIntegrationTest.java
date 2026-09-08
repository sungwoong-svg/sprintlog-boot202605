package com.sprintlog.sprintlogboot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.domain.Visibility;
import com.sprintlog.sprintlogboot.dto.request.CreateActivityRequest;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import com.sprintlog.sprintlogboot.repository.AuditLogRepository;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

// 서비스 계층의 통합 테스트 (컨트롤러 제외)
@SpringBootTest // Spring의 모든 빈을 등록하여 컨테이터에 세팅
@ActiveProfiles("test") // 테스트 실행 시 프로필 active를 test로 -> test.yml을 활성화
@DisplayName("ActivityService 통합 테스트")
public class ActivityServiceIntegrationTest {

  @Autowired
  ActivityService service;
  @Autowired
  ActivityRepository activityRepository;
  @Autowired
  AuditLogRepository auditLogRepository;
  @Autowired
  UserRepository userRepository;
  @Autowired
  PasswordEncoder passwordEncoder;

  @BeforeEach
  void clean() {
    activityRepository.deleteAll();
    auditLogRepository.deleteAll();
    userRepository.deleteAll();
    
    userRepository.save(new User("김춘식", "choon@naver.com", passwordEncoder.encode("password123")));
  }

  @Test
  @DisplayName("create — 진짜 DB에 저장되고 다시 꺼내 확인된다")
  void create_는_진짜_DB에_저장된다() {
    // create(request, savedFileName) — 파일 없으면 두 번째 인자 null.
    LearningActivity saved = service.create(new CreateActivityRequest(
        ActivityCategory.LECTURE, "통합 테스트 강의", 60, Visibility.PUBLIC,
        null, null, "이강사", null, null), null, "choon@naver.com");

    // 진짜 DB 에서 다시 꺼내 확인(가짜라면 못 하는, 실제 영속 검증).
    assertThat(saved.getId()).isNotNull();
    assertThat(activityRepository.findById(saved.getId()))
        .isPresent()
        .get()
        .extracting(LearningActivity::getTitle)
        .isEqualTo("통합 테스트 강의");
  }

  // 원자성 — demoAtomicRegister 는 한 트랜잭션에서 활동 + 이력 을 함께 저장한다.
  // 가짜(Mockito)로는 진짜 커밋/롤백이 없어 이 "둘 다/둘 다 안" 을 검증할 수 없다.
  @Test
  @DisplayName("원자성 — 등록 도중 실패하면 활동·이력이 둘 다 롤백된다")
  void 실패하면_둘_다_롤백() {
    long activitiesBefore = activityRepository.count();
    long logsBefore = auditLogRepository.count();

    // fail=true → 활동·이력을 저장한 뒤 예외 → 트랜잭션 롤백
    assertThatThrownBy(() -> service.demoAtomicRegister(true))
        .isInstanceOf(IllegalArgumentException.class);

    // 반쪽 상태가 안 생긴다 — 둘 다 원래 개수 그대로
    assertThat(activityRepository.count()).isEqualTo(activitiesBefore);
    assertThat(auditLogRepository.count()).isEqualTo(logsBefore);
  }

  @Test
  @DisplayName("정상 등록 — 활동·이력이 함께 커밋된다")
  void 성공하면_둘_다_커밋() {
    long activitiesBefore = activityRepository.count();
    long logsBefore = auditLogRepository.count();

    service.demoAtomicRegister(false);

    assertThat(activityRepository.count()).isEqualTo(activitiesBefore + 1);
    assertThat(auditLogRepository.count()).isEqualTo(logsBefore + 1);
  }

  // 전파(REQUIRES_NEW) — 오직 통합 테스트만 검증할 수 있는 것. 가짜(Mockito)로는 트랜잭션 프록시가
  //   없어 이 동작이 아예 재현되지 않는다. "본 작업은 실패해도 '시도했다는 기록'은 남아야 한다" 는 감사 로그의 전형.
  @Test
  @DisplayName("전파(REQUIRES_NEW) — 활동은 롤백돼도 '시도 이력'은 남는다")
  void 전파_활동은_롤백_시도이력은_남음() {
    long activitiesBefore = activityRepository.count();
    long logsBefore = auditLogRepository.count();

    // logAttempt(REQUIRES_NEW)로 시도 이력을 독립 커밋 → 활동 저장 → fail=true 로 부모만 롤백
    assertThatThrownBy(() -> service.demoPropagation(true))
        .isInstanceOf(IllegalStateException.class);

    assertThat(activityRepository.count()).isEqualTo(activitiesBefore);      // 활동은 롤백(변화 없음)
    assertThat(auditLogRepository.count()).isEqualTo(logsBefore + 1);        // 시도 이력은 남음(독립 트랜잭션)
  }

}
