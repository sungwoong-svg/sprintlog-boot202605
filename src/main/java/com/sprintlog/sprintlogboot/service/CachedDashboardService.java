package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@Deprecated
public class CachedDashboardService {

  private final ActivityDashboard dashboard;
  private final CacheManager cacheManager;

  private static final String KEY = "all";

  public ActivityDashboard.Summary summarize() {
    Cache cache = cacheManager.getCache(CacheConfig.ACTIVITY_SUMMARY);

    // 1. 먼저 캐시를 본다.
    Cache.ValueWrapper found = cache.get(KEY);
    if (found != null) {
      log.info("[캐시 히트] {} - 원본을 부르지 않는다.", CacheConfig.ACTIVITY_SUMMARY);
      return (ActivityDashboard.Summary) found.get();
    }

    // 2. 없으면 원본을 부른다.
    log.info("[캐시 미스] {} - 원본을 부른다.", CacheConfig.ACTIVITY_SUMMARY);
    ActivityDashboard.Summary value = dashboard.summarize();

    // 3. 캐시에 적어둔다.
    cache.put(KEY, value);

    return value;
  }

  public void evict() {
    cacheManager.getCache(CacheConfig.ACTIVITY_SUMMARY).clear();
    log.info("[캐시 비움] {}", CacheConfig.ACTIVITY_SUMMARY);
  }
}
