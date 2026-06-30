package com.sprintlog.sprintlogboot.lifecycle;

import com.sprintlog.sprintlogboot.config.SprintLogProperties;
import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
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
        if (userRepository.count() == 0) {
            User choon = new User("김춘식", "choon@naver.com");
            userRepository.save(choon);
            User hong = new User("홍길동", "hong@gmail.com");
            User saved = userRepository.save(hong);
            log.info("[lifecycle] User 저장 완료 - saved id={}, createdAt={}"
                , saved.getId(), saved.getCreatedAt());


            LearningActivity l1 = new LearningActivity(
                ActivityCategory.LECTURE, "Spring Bean Scope", 90, Visibility.PUBLIC, "이강사", null, null);
            l1.assignOwner(choon);
            repository.save(l1);
            LearningActivity l2 = new LearningActivity(
                ActivityCategory.PRACTICE, "@PostConstruct 실습", 60, Visibility.PUBLIC, null, 85, null);
            l2.assignOwner(choon);
            repository.save(l2);

            LearningActivity l3 = new LearningActivity(
                ActivityCategory.READING, "스프링 인 액션", 75, Visibility.PUBLIC, null, null, "스프링 인 액션 5판");
            l3.assignOwner(hong);
            repository.save(l3);
            LearningActivity l4 = new LearningActivity(
                ActivityCategory.LECTURE, "Prototype vs Singleton", 45, Visibility.PRIVATE, "이강사", null, null);
            l4.assignOwner(hong);
            repository.save(l4);

            for (int i = 1; i < 123 ; i++) {
                LearningActivity activity = new LearningActivity(
                    ActivityCategory.LECTURE, "test" + i, 60, Visibility.PUBLIC, "test" + i, null, null);
                activity.assignOwner(choon);
                repository.save(activity);
            }

        }




        log.info("[lifecycle] 샘플 데이터 적재 완료 — 총 {}개", repository.count());

        log.info("DB 사용자 수: {}명", userRepository.count());

    }

    @PreDestroy
    public void shutdown() {
        log.info("[lifecycle] @PreDestroy — DataInitializer 가 종료 정리를 합니다.");


    }

}