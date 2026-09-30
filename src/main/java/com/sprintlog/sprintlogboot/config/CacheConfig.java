package com.sprintlog.sprintlogboot.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

  // 활동 통계 캐시 이름 
  public static final String ACTIVITY_SUMMARY = "activitySummary";

}
