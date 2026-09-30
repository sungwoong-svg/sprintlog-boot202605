package com.sprintlog.sprintlogboot.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

  // 활동 통계 캐시 이름
  public static final String ACTIVITY_SUMMARY = "activitySummary";

  // 주간 목표 달성률 캐시
  public static final String ACHIEVEMENT_RATE = "achievementRate";

}
