package com.sprintlog.sprintlogboot.monitoring;

import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SprintLogHealthIndicator implements HealthIndicator {

  private final ActivityRepository activityRepository;

  @Override
  public Health health() {
    try {
      long count = activityRepository.count(); // 핵심 데이터에 실제로 닿아 본다.
      return Health.up()
          .withDetail("activityCount", count)
          .withDetail("message", "활동 데이터 정상 조회")
          .build();
    } catch (Exception e) {
      // 조회 실패 == 우리 앱이 핵심 기능을 못함 -> DOWN(원인 예외 포함)
      return Health.down(e)
          .withDetail("message", "활동 데이터 조회 실패")
          .build();
    }
  }
}
