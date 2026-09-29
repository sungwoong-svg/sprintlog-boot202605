package com.sprintlog.sprintlogboot.lifecycle;

import com.sprintlog.sprintlogboot.config.SprintLogProperties;
import com.sprintlog.sprintlogboot.domain.ActivityAuditLog;
import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.domain.Role;
import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.domain.Visibility;
import com.sprintlog.sprintlogboot.domain.WeeklyGoal;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import com.sprintlog.sprintlogboot.repository.AuditLogRepository;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import com.sprintlog.sprintlogboot.repository.WeeklyGoalRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j // log 라는 이름으로 SLF4J 로거 자동 생성
public class DataInitializer {

    private final ActivityRepository repository;
    private final SprintLogProperties properties;
    // 우리가 직접 UserRepository 빈 등록은 하지 않았지만 Spring data jpa가 이미 구현체를 빈으로 등록해 놨습니다.
    private final UserRepository userRepository;

    private final AuditLogRepository auditLogRepository;
    private final WeeklyGoalRepository goalRepository;

    // 비밀번호 암호화를 위한 빈 주입
    private final PasswordEncoder passwordEncoder;

    // 주입된 의존성 객체를 가지고 무언가 해야 할 로직을 작성.
    @PostConstruct
    public void loadSampleData() {

        log.info("[lifecycle] @PostConstruct — {}", properties.getWelcomeMessage());

        if (!properties.getSampleData().isEnabled()) {
            log.info("[lifecycle] sample-data.enabled = false - 적재 건너뜀!");
            return;
        }

        log.info("[lifecycle] @PostConstruct — DataInitializer 가 샘플 데이터를 적재합니다.");
        if (userRepository.count() == 0) {

            User admin = new User("관리자", "admin@sprintlog.com", passwordEncoder.encode("admin123"),
                Role.ADMIN);
            userRepository.save(admin);

            User choon = new User("김춘식", "choon@naver.com", passwordEncoder.encode("password123"));
            LearningActivity l1 = new LearningActivity(
                ActivityCategory.LECTURE, "Spring Bean Scope", 90, Visibility.PUBLIC, "이강사", null, null);
            LearningActivity l2 = new LearningActivity(
                ActivityCategory.PRACTICE, "@PostConstruct 실습", 60, Visibility.PUBLIC, null, 85, null);
            choon.getActivities().add(l1);
            choon.getActivities().add(l2);
            userRepository.save(choon);

            WeeklyGoal choonGoal = new WeeklyGoal(60);
            choonGoal.assignUser(choon);
            goalRepository.save(choonGoal);

            User hong = new User("홍길동", "hong@gmail.com", passwordEncoder.encode("hong123"));
            LearningActivity l3 = new LearningActivity(
                ActivityCategory.READING, "스프링 인 액션", 75, Visibility.PUBLIC, null, null, "스프링 인 액션 5판");
            LearningActivity l4 = new LearningActivity(
                ActivityCategory.LECTURE, "Prototype vs Singleton", 45, Visibility.PRIVATE, "이강사", null, null);
            hong.getActivities().add(l3);
            hong.getActivities().add(l4);

            User saved = userRepository.save(hong);
            log.info("[lifecycle] User 저장 완료 - saved id={}, createdAt={}"
                , saved.getId(), saved.getCreatedAt());

        }




        log.info("[lifecycle] 샘플 데이터 적재 완료 — 총 {}개", repository.count());

        if (auditLogRepository.count() == 0) {
            auditLogRepository.save(new ActivityAuditLog("ACTIVITY_CREATE", "활동 등록 - '스프링 시큐리티 강의' (choon@naver.com)"));
            auditLogRepository.save(new ActivityAuditLog("ACTIVITY_UPDATE", "활동 수정 - 학습 시간 30분 → 60분 (choon@naver.com)"));
            auditLogRepository.save(new ActivityAuditLog("ACTIVITY_DELETE", "활동 삭제 - '읽다 만 책' (hong@gmail.com)"));
            auditLogRepository.save(new ActivityAuditLog("ROLE_CHANGE", "권한 변경 - hong@gmail.com: USER → ADMIN (관리자 수행)"));
            log.info("[lifecycle] 감사 로그 샘플 {}건 생성", auditLogRepository.count());
        }

        log.info("DB 사용자 수: {}명", userRepository.count());

    }

    @PreDestroy
    public void shutdown() {
        log.info("[lifecycle] @PreDestroy — DataInitializer 가 종료 정리를 합니다.");


    }

}