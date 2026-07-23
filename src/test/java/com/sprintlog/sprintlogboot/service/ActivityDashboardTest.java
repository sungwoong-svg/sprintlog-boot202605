package com.sprintlog.sprintlogboot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.domain.Visibility;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("여기는 ActivityDashboard - Mockito 단위")
class ActivityDashboardTest {

  @Mock
  ActivityRepository repository;

  @InjectMocks
  ActivityDashboard dashboard;

  private LearningActivity activity(int minutes) {
    return new LearningActivity(
        ActivityCategory.LECTURE, "학습", minutes, Visibility.PUBLIC, 
        "이강사", null, null        
    );
  }
  
  @Test
  @DisplayName("총 학습시간 60분, 목표 120분이면 달성률 50%")
  void 달성률_50퍼센트() {
    // given
    given(repository.findAll()).willReturn(List.of(activity(20), activity(40)));
  
    // when
    int rate = dashboard.achievementRate(120);

    // then
    assertThat(rate).isEqualTo(50);
  }

  @Test
  @DisplayName("목표를 넘겨도 100% 상한")
  void 초과해도_100퍼센트() {
    // given
    given(repository.findAll()).willReturn(List.of(activity(200), activity(40)));

    // when & then
    assertThat(dashboard.achievementRate(60)).isEqualTo(100);
  }
}