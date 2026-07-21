package com.sprintlog.sprintlogboot.repository;

import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.domain.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

//@SpringBootTest 내가 등록한, Spring Boot에서 사용하는 모든 빈들이 로딩되어 컨테이너에 세팅됨
@DataJpaTest // JPA 계층 관련 빈만 로딩(Service, Controller, Component는 로딩되지 않음)
class ActivityRepositoryTest {

  @Autowired // 테스트 환경에서는 생성자 의존성 주입을 사용할 수 없어서 @Autowired로 직접 주입해 주세요.
  ActivityRepository activityRepository;

  @Autowired
  TestEntityManager em;

  @BeforeEach
  void setUp() {
    persist(ActivityCategory.LECTURE,  "Spring Boot 입문", 90,  Visibility.PUBLIC,  "spring", "java");
    persist(ActivityCategory.LECTURE,  "JPA 심화",         120, Visibility.PUBLIC,  "spring", "jpa");
    persist(ActivityCategory.READING,  "클린 코드",         60,  Visibility.PRIVATE, "clean");
    persist(ActivityCategory.PRACTICE, "알고리즘 연습",     45,  Visibility.PUBLIC);

    // 더미 데이터 세팅 후에 진행될 테스트를 좀 더 깔끔하게 진행하기 위해서 TestEntityManager로 영속성 컨텍스트를 직접 제어.
    em.flush(); // 영속성 컨테스트에 영속된 엔티티들을 강제로 밀어내기 -> INSERT
    em.clear(); // 영속성 컨텍스트 비우기
  }

  private void persist(ActivityCategory category, String title, int minutes, Visibility visibility, String... tags) {
    LearningActivity activity = new LearningActivity(category, title, minutes, visibility, null, null, null);
    for (String tag : tags) {
      activity.addTag(tag);
    }
    em.persist(activity);
  }

}