package com.sprintlog.sprintlogboot.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@EnableCaching
public class CacheConfig {

  // 활동 통계 캐시 이름
  public static final String ACTIVITY_SUMMARY = "activitySummary";

  // 주간 목표 달성률 캐시
  public static final String ACHIEVEMENT_RATE = "achievementRate";

  @Bean
  public ApplicationRunner cacheStartupReport(CacheManager cacheManager) {
    return args -> {
      log.info("[캐시] 매니저 = {}", cacheManager.getClass().getSimpleName());
      cacheManager.getCacheNames().forEach(name ->
          log.info("[캐시]   {} → {}", name,
              cacheManager.getCache(name).getNativeCache().getClass().getSimpleName()));
    };
  }

  @Bean
  public Caffeine<Object, Object> caffeineConfig(@Value("${spring.cache.max-size}") long maxSize,
                             @Value("${spring.cache.ttl-seconds}") long ttlSeconds) {
    return Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
        .recordStats()
        .removalListener((Object key, Object value, RemovalCause cause) -> {
          log.info("[캐시 제거] 키={} 사유={} (쫓겨난 것인가={})", key, cause, cause.wasEvicted());
        });

  }

}
