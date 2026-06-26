package com.sprintlog.sprintlogboot.lifecycle;

import com.sprintlog.sprintlogboot.config.SprintLogProperties;
import com.sprintlog.sprintlogboot.domain.LectureLog;
import com.sprintlog.sprintlogboot.domain.PracticeLog;
import com.sprintlog.sprintlogboot.domain.ReadingLog;
import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.domain.Visibility;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
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

    // 주입된 의존성 객체를 가지고 무언가 해야 할 로직을 작성.
    @PostConstruct
    public void loadSampleData() {

        log.info("[lifecycle] @PostConstruct — {}", properties.getWelcomeMessage());

        if (!properties.getSampleData().isEnabled()) {
            log.info("[lifecycle] sample-data.enabled = false - 적재 건너뜀!");
            return;
        }

        log.info("[lifecycle] @PostConstruct — DataInitializer 가 샘플 데이터를 적재합니다.");

        repository.add(new LectureLog("Spring Bean Scope", 90, Visibility.PUBLIC, "이강사"));
        repository.add(new PracticeLog("@PostConstruct 실습", 60, Visibility.PUBLIC, 85));
        repository.add(new ReadingLog("스프링 인 액션", 75, Visibility.PUBLIC, "스프링 인 액션 5판"));
        repository.add(new LectureLog("Prototype vs Singleton", 45, Visibility.PRIVATE, "이강사"));

        log.info("[lifecycle] 샘플 데이터 적재 완료 — 총 {}개", repository.count());

        if (userRepository.count() == 0) {
            User choon = new User("김춘식", "choon@naver.com");
            userRepository.save(choon);
            User saved = userRepository.save(new User("홍길동", "hong@gmail.com"));
            log.info("User 저장 완료 - saved id={}, createdAt={}", saved.getId(), saved.getCreatedAt());
        }

        log.info("DB 사용자 수: {}명", userRepository.count());

    }

    @PreDestroy
    public void shutdown() {
        log.info("[lifecycle] @PreDestroy — DataInitializer 가 종료 정리를 합니다.");
        log.info("[lifecycle] 최종 활동 수: {}개, 총 학습 시간: {}분"
                , repository.count(), repository.getTotalMinutes());

    }

}