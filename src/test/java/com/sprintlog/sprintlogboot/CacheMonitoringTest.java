package com.sprintlog.sprintlogboot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.sprintlog.sprintlogboot.config.CacheConfig;
import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.domain.Visibility;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import com.sprintlog.sprintlogboot.service.ActivityDashboard;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.cache.CachesEndpoint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("캐시가 일을 하고 있는지 본다")
class CacheMonitoringTest {

  @Autowired CacheManager cacheManager;
  @Autowired MeterRegistry meterRegistry;
  @Autowired ActivityDashboard dashboard;
  @Autowired CachesEndpoint cachesEndpoint;
  @Autowired 일부러_틀린_설정 틀린_설정;
  @Autowired ActivityRepository activityRepository;

  @BeforeEach
  void clearCaches() {
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  // ⚠ 계기판 숫자는 앱이 뜬 뒤로 계속 더해진다. 그래서 '지금 값' 이 아니라 '내가 만든 만큼' 을 본다.
  private double 계기판(String 캐시, String 결과) {
    return meterRegistry.get("cache.gets").tag("cache", 캐시).tag("result", 결과)
        .functionCounter().count();
  }

  private double 제거수(String 사유) {
    var counter = meterRegistry.find("sprintlog.cache.removals").tag("cause", 사유).counter();
    return counter == null ? 0 : counter.count();
  }

  /**
   * 달성률이 0 이 아니게 만든다.
   * ⚠ 0 이면 unless = "#result == 0" 때문에 아예 캐시에 안 담긴다 — 그러면 100번 부르면 100번 원본이 도는데,
   *   그건 폭발이 아니라 '캐시가 없는 것' 이다. 대조군이 가짜 증거가 된다(작성 중 실제로 그랬다).
   */
  private void 활동을_하나_심는다() {
    if (activityRepository.count() == 0) {
      activityRepository.save(new LearningActivity(
          ActivityCategory.LECTURE, "캐시 테스트용 강의", 120, Visibility.PUBLIC, "이강사", null, null));
    }
  }

  /** 출발 신호에 맞춰 n 개의 요청을 한꺼번에 보낸다(비동기 파트의 병렬 호출기와 같은 모양). */
  private void 동시에(int n, Supplier<?> 요청) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(n);
    CountDownLatch 출발 = new CountDownLatch(1);
    List<Future<?>> 결과들 = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      결과들.add(pool.submit(() -> { 출발.await(); return 요청.get(); }));
    }
    출발.countDown();
    for (Future<?> f : 결과들) f.get(10, TimeUnit.SECONDS);
    pool.shutdown();
  }

  /**
   * 라벨을 손으로 적지 않고 '실제 상태' 에서 만든다.
   * 손으로 "(sync = true)" 라고 적어 두면, sync 를 붙이기 전에 돌려도 화면이 그렇게 거짓말을 한다.
   */
  private String 상태(String 메서드, Class<?>... 인자타입) {
    try {
      Cacheable 설정 = ActivityDashboard.class.getMethod(메서드, 인자타입).getAnnotation(Cacheable.class);
      return 메서드 + " · sync = " + 설정.sync();
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  private int 원본_실행_횟수(String 로그, String 메서드) {
    String 표식 = "⏰ ActivityDashboard." + 메서드;
    int n = 0, i = 0;
    while ((i = 로그.indexOf(표식, i)) >= 0) { n++; i++; }
    return n;
  }

  @Nested
  @DisplayName("켜 둔 숫자를 읽는다")
  class 숫자를_읽는다 {

    @Test
    @DisplayName("적중과 빗나감을 세면 적중률이 나온다")
    void 적중률() {
      double 적중_전 = 계기판(CacheConfig.ACHIEVEMENT_RATE, "hit");
      double 빗나감_전 = 계기판(CacheConfig.ACHIEVEMENT_RATE, "miss");

      Cache 캐시 = cacheManager.getCache(CacheConfig.ACHIEVEMENT_RATE);
      캐시.put(300, 40);
      for (int i = 0; i < 9; i++) 캐시.get(300);   // 아홉 번 맞히고
      캐시.get(999);                                 // 한 번 빗나간다

      double 적중 = 계기판(CacheConfig.ACHIEVEMENT_RATE, "hit") - 적중_전;
      double 빗나감 = 계기판(CacheConfig.ACHIEVEMENT_RATE, "miss") - 빗나감_전;
      double 적중률 = 적중 / (적중 + 빗나감) * 100;

      System.out.printf("[관찰] 적중 %.0f · 빗나감 %.0f → 적중률 %.0f%%%n", 적중, 빗나감, 적중률);
      assertThat(적중률).isEqualTo(90.0);
    }
  }

  @Nested
  @DisplayName("캐시 엔드포인트를 연다")
  class 엔드포인트 {

    @Test
    @DisplayName("caches 엔드포인트가 캐시 목록과 구현체를 보여 준다")
    void 목록() {
      var 응답 = cachesEndpoint.caches();
      응답.getCacheManagers().forEach((매니저, 내용) ->
          내용.getCaches().forEach((이름, 캐시) ->
              System.out.println("[관찰] " + 매니저 + " / " + 이름 + " → "
                  + 캐시.getTarget().substring(캐시.getTarget().lastIndexOf('$') + 1))));

      assertThat(응답.getCacheManagers().get("cacheManager").getCaches())
          .containsKeys(CacheConfig.ACTIVITY_SUMMARY, CacheConfig.ACHIEVEMENT_RATE);
    }
  }

  @Nested
  @DisplayName("캐시 폭발(스탬피드) — 비는 순간 한꺼번에 몰린다")
  class 스탬피드 {

    @Test
    @DisplayName("sync 가 없으면 동시 요청 여럿이 원본으로 간다 — sync 를 못 붙인 달성률이 대조군이다")
    void sync_없으면(CapturedOutput output) throws Exception {
      활동을_하나_심는다();
      // 가드 ① — 이 메서드가 '진짜로 캐시되는' 상태인지 먼저 확인한다(0% 면 unless 가 막는다).
      assertThat(dashboard.achievementRate(300)).isNotZero();
      cacheManager.getCache(CacheConfig.ACHIEVEMENT_RATE).clear();

      double 빗나감_전 = 계기판(CacheConfig.ACHIEVEMENT_RATE, "miss");
      int 전 = 원본_실행_횟수(output.getAll(), "achievementRate");

      동시에(100, () -> dashboard.achievementRate(300));

      int 원본 = 원본_실행_횟수(output.getAll(), "achievementRate") - 전;
      double 빗나감 = 계기판(CacheConfig.ACHIEVEMENT_RATE, "miss") - 빗나감_전;
      System.out.printf("[관찰] (%s) 요청 100 → 원본 %d번 · miss +%.0f%n",
          상태("achievementRate", int.class), 원본, 빗나감);

      // 가드 ② — 끝나고 나면 캐시에 값이 있어야 한다. 없으면 '안 담긴 것' 이지 '폭발' 이 아니다.
      assertThat(cacheManager.getCache(CacheConfig.ACHIEVEMENT_RATE).get(300)).isNotNull();

      // 몇 번인지는 매번 다르다(타이밍). 확실한 건 '한 번이 아니다' 라는 것이다.
      assertThat(원본).isGreaterThan(1);
    }

    @Test
    @DisplayName("sync = true 면 한 명만 계산하고 나머지는 기다렸다 받는다")
    void sync_있으면(CapturedOutput output) throws Exception {
      double 적중_전 = 계기판(CacheConfig.ACTIVITY_SUMMARY, "hit");
      double 빗나감_전 = 계기판(CacheConfig.ACTIVITY_SUMMARY, "miss");
      int 전 = 원본_실행_횟수(output.getAll(), "summarize");

      동시에(100, () -> dashboard.summarize());

      int 원본 = 원본_실행_횟수(output.getAll(), "summarize") - 전;
      double 적중 = 계기판(CacheConfig.ACTIVITY_SUMMARY, "hit") - 적중_전;
      double 빗나감 = 계기판(CacheConfig.ACTIVITY_SUMMARY, "miss") - 빗나감_전;
      System.out.printf("[관찰] (%s) 요청 100 → 원본 %d번 · hit +%.0f · miss +%.0f%n",
          상태("summarize"), 원본, 적중, 빗나감);

      assertThat(원본).isEqualTo(1);
      assertThat(빗나감).isEqualTo(1);
      assertThat(적중).isEqualTo(99);
    }

    @Test
    @DisplayName("⚠ sync 는 unless 와 같이 못 쓴다 — 기동이 아니라 '호출하는 순간' 터진다")
    void sync_와_unless() {
      // 여기까지 왔다는 것 자체가 '기동은 멀쩡했다' 는 증거다.
      System.out.println("[관찰] 기동은 됐다");
      Throwable 예외 = catchThrowable(() -> 틀린_설정.달성률(300));
      System.out.println("[관찰] 호출하는 순간 → " + 예외.getClass().getSimpleName());
      // 메시지 뒤쪽은 메서드 설정 전체를 늘어놓아 길다 — 앞의 한 문장만 보인다.
      System.out.println("[관찰] 메시지: " + 예외.getMessage().split(" on '")[0]);

      assertThat(예외)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("does not support the unless attribute");
    }
  }

  @Nested
  @DisplayName("커스텀 지표 — 왜 지워졌는지를 계기판에 올린다")
  class 커스텀_지표 {

    @Test
    @DisplayName("우리가 지운 것(EXPLICIT)도 센다 — 라이브러리 지표는 이걸 안 센다")
    void 사유별로_센다() {
      double 명시_전 = 제거수("EXPLICIT");

      Cache 캐시 = cacheManager.getCache(CacheConfig.ACTIVITY_SUMMARY);
      캐시.put("키", "값");
      캐시.clear();      // @CacheEvict(allEntries = true) 가 부르는 바로 그 길

      // 리스너는 ForkJoinPool.commonPool 에서 돈다 — 조금 늦게 도착한다.
      await().atMost(Duration.ofSeconds(3)).until(() -> 제거수("EXPLICIT") > 명시_전);
      System.out.printf("[관찰] sprintlog.cache.removals{cause=EXPLICIT} +%.0f%n", 제거수("EXPLICIT") - 명시_전);
    }

    @Test
    @DisplayName("상한에 밀려 쫓겨난 것은 SIZE 로 센다")
    void SIZE_도_센다() {
      double 크기_전 = 제거수("SIZE");
      var 속 = (com.github.benmanes.caffeine.cache.Cache<?, ?>)
          cacheManager.getCache(CacheConfig.ACHIEVEMENT_RATE).getNativeCache();

      Cache 캐시 = cacheManager.getCache(CacheConfig.ACHIEVEMENT_RATE);
      for (int 목표 = 100; 목표 <= 1000; 목표 += 100) 캐시.put(목표, 목표);   // 상한(테스트 5칸)을 넘긴다
      속.cleanUp();

      await().atMost(Duration.ofSeconds(3)).until(() -> 제거수("SIZE") > 크기_전);
      System.out.printf("[관찰] sprintlog.cache.removals{cause=SIZE} +%.0f%n", 제거수("SIZE") - 크기_전);
    }
  }

  /**
   * sync 와 unless 를 같이 쓰면 어떻게 되는지 보여 주려고 '일부러 틀리게' 만든 빈.
   * 운영 코드(ActivityDashboard)를 틀리게 만들 수는 없으니 테스트 안에만 둔다.
   */
  @TestConfiguration
  static class 틀린_설정_구성 {
    @Bean
    일부러_틀린_설정 일부러_틀린_설정() {
      return new 일부러_틀린_설정();
    }
  }

  static class 일부러_틀린_설정 {
    @Cacheable(value = CacheConfig.ACHIEVEMENT_RATE, key = "#goal", unless = "#result == 0", sync = true)
    public int 달성률(int goal) {
      return 40;
    }
  }
}
