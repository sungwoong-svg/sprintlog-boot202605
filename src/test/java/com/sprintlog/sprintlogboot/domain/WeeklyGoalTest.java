package com.sprintlog.sprintlogboot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WeeklyGoal (주간 학습 목표 - TDD)")
public class WeeklyGoalTest {

  @Nested
  @DisplayName("달성률 계산")
  class AchievementRate {
    @Test
    @DisplayName("목표 60분에 30분 공부하면 50%")
    void 절반이면_50퍼센트() {
      WeeklyGoal goal = new WeeklyGoal(60);
      assertThat(goal.achievementRate(30)).isEqualTo(50);
    }

    @Test
    @DisplayName("목표만큼 채우면 100%")
    void 목표를_채우면_100퍼센트() {
      WeeklyGoal goal = new WeeklyGoal(60);
      assertThat(goal.achievementRate(60)).isEqualTo(100);
    }

    @Test
    @DisplayName("목표를 초과해도 100% 상한을 가진다.")
    void 초과해도_100퍼센트_상한() {
      WeeklyGoal goal = new WeeklyGoal(60);
      assertThat(goal.achievementRate(90)).isEqualTo(100);
    }

    @Test
    @DisplayName("공부시간이 0이면 0%")
    void 안하면_0퍼센트() {
      WeeklyGoal goal = new WeeklyGoal(60);
      assertThat(goal.achievementRate(0)).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("목표 달성 여부")
  class Achieved {

    @Test
    @DisplayName("목표 이상 공부하면 달성")
    void 목표_이상이면_달성() {
      WeeklyGoal goal = new WeeklyGoal(60);
      assertThat(goal.isAchieved(60)).isTrue();
      assertThat(goal.isAchieved(59)).isFalse();
    }

  }

  @Nested
  @DisplayName("생성 규칙")
  class CreationRule {

    @Test
    @DisplayName("목표가 0이하면 만들 수 없다.")
    void 목표가_0이하면_예외() {
      assertThatThrownBy(() -> new WeeklyGoal(0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("1분 이상");
    }

  }

  // 두 번째 TDD 사이클 - 목표 시간 대비 공부 시간 계산해서 남은 시간이 얼마인지를 알려주는 remainingMinutes
  @Nested
  @DisplayName("남은 시간")
  class Remaining {
    @Test
    @DisplayName("목표 60분에 20분 하면 40분 남음")
    void 남은_시간() {
      assertThat(new WeeklyGoal(60).remainingMinutes(20)).isEqualTo(40);
    }

    @Test
    @DisplayName("목표를 넘겨도 음수가 아니라 0")
    void 넘기면_0() {
      assertThat(new WeeklyGoal(60).remainingMinutes(90)).isEqualTo(0);
    }
  }

}
