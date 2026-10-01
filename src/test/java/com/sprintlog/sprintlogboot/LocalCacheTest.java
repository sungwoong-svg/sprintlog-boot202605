package com.sprintlog.sprintlogboot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.sprintlog.sprintlogboot.config.CacheConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("얼마나, 언제까지 들고 있을까")
class LocalCacheTest {

  // application-test.yml 의 값. 운영은 500칸·60초지만 테스트는 기다릴 수 없어 줄여 두었다.
  private static final int 테스트_상한 = 5;
  private static final long 테스트_수명_초 = 1;

  @Autowired CacheManager cacheManager;
  @Autowired MeterRegistry meterRegistry;

  @BeforeEach
  void clearCaches() {
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  /** 스프링 Cache 뒤에 있는 진짜 Caffeine 캐시를 꺼낸다. */
  private com.github.benmanes.caffeine.cache.Cache<Object, Object> 속을_꺼낸다(String 이름) {
    @SuppressWarnings("unchecked")
    var native캐시 = (com.github.benmanes.caffeine.cache.Cache<Object, Object>)
        cacheManager.getCache(이름).getNativeCache();
    return native캐시;
  }

  @Nested
  @DisplayName("구현체를 갈아끼웠다")
  class 갈아끼운다 {

    @Test
    @DisplayName("코드는 한 줄도 안 고쳤는데 캐시 매니저가 바뀌었다")
    void 매니저가_바뀌었다() {
      // 의존성 두 줄(starter-cache + caffeine)만 추가했다.
      // @Cacheable 도 CacheConfig 의 이름 상수도 그대로다.
      System.out.println("[관찰] 캐시 매니저 = " + cacheManager.getClass().getSimpleName());
      assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
    }

    @Test
    @DisplayName("설정을 해야 비로소 '상한 있는' 캐시가 된다")
    void 상한이_생겼다() {
      // 클래스 이름이 곧 답이다. 설정 전에는 UnboundedLocalCache 였다.
      String 속 = 속을_꺼낸다(CacheConfig.ACHIEVEMENT_RATE).getClass().getSimpleName();
      System.out.println("[관찰] 캐시 속 = " + 속);
      assertThat(속).contains("Bounded");
    }

    @Test
    @DisplayName("기동 로그 세 가지 — 지난 시간 / 의존성만 넣었을 때 / 설정까지 한 지금")
    void 세_가지_속() {
      // 지난 시간 상태
      var 옛_매니저 = new ConcurrentMapCacheManager(
          CacheConfig.ACTIVITY_SUMMARY, CacheConfig.ACHIEVEMENT_RATE);
      // 의존성만 넣고 아무 설정도 안 했을 때
      var 설정_없는_매니저 = new CaffeineCacheManager(
          CacheConfig.ACTIVITY_SUMMARY, CacheConfig.ACHIEVEMENT_RATE);

      기동로그를_찍는다("(지난 시간)", 옛_매니저);
      기동로그를_찍는다("(의존성만)", 설정_없는_매니저);
      기동로그를_찍는다("(설정까지)", cacheManager);

      assertThat(속이름(옛_매니저)).isEqualTo("ConcurrentHashMap");
      assertThat(속이름(설정_없는_매니저)).isEqualTo("UnboundedLocalManualCache");
      assertThat(속이름(cacheManager)).isEqualTo("BoundedLocalManualCache");
    }

    private void 기동로그를_찍는다(String 딱지, CacheManager 매니저) {
      System.out.println(딱지 + " [캐시] 매니저 = " + 매니저.getClass().getSimpleName());
      매니저.getCacheNames().stream().sorted().forEach(name ->
          System.out.println(딱지 + " [캐시]   " + name + " → "
              + 매니저.getCache(name).getNativeCache().getClass().getSimpleName()));
    }

    private String 속이름(CacheManager 매니저) {
      return 매니저.getCache(CacheConfig.ACHIEVEMENT_RATE)
          .getNativeCache().getClass().getSimpleName();
    }

    @Test
    @DisplayName("⚠ 설정을 안 하면 Caffeine 도 무제한이다 — 클래스 이름이 그렇게 말한다")
    void 설정을_안_하면_무제한() {
      // 우리 캐시는 이미 설정돼 있으니, 설정 안 한 Caffeine 을 따로 하나 만들어 본다.
      // 의존성만 추가하고 아무것도 안 했을 때가 바로 이 상태였다.
      var 상한없는_캐시 = Caffeine.newBuilder().build();
      System.out.println("[관찰] 캐시 속 = " + 상한없는_캐시.getClass().getSimpleName());

      for (int i = 0; i < 100_000; i++) {
        상한없는_캐시.put(i, i);
      }
      상한없는_캐시.cleanUp();
      System.out.println("[관찰] 넣은 개수 = 100,000");
      System.out.println("[관찰] 남은 개수 = " + 상한없는_캐시.estimatedSize());

      assertThat(상한없는_캐시.getClass().getSimpleName()).contains("Unbounded");
      assertThat(상한없는_캐시.estimatedSize()).isEqualTo(100_000);   // 하나도 안 버렸다
    }
  }

  @Nested
  @DisplayName("크기 — 넘치면 쫓아낸다")
  class 크기 {

    @Test
    @DisplayName("상한을 넘겨 넣어도 칸 수는 상한에서 멈춘다")
    void 상한에서_멈춘다() {
      Cache 캐시 = cacheManager.getCache(CacheConfig.ACHIEVEMENT_RATE);
      var 속 = 속을_꺼낸다(CacheConfig.ACHIEVEMENT_RATE);
      // ⚠ 쫓아낸 수도 앱이 뜬 뒤로 계속 더해지는 값이다 — '내가 만든 만큼' 을 본다.
      long 쫓아낸_전 = 속.stats().evictionCount();

      for (int 목표 = 1; 목표 <= 50; 목표++) {
        캐시.put(목표, 목표);
      }
      속.cleanUp();   // 쫓아내기는 뒤에서 처리된다 — 테스트에서만 재촉한다

      System.out.println("[관찰] 넣은 개수 = 50");
      System.out.println("[관찰] 남은 개수 = " + 속.estimatedSize());
      System.out.println("[관찰] 쫓아낸 수 = " + (속.stats().evictionCount() - 쫓아낸_전));

      assertThat(속.estimatedSize()).isEqualTo(테스트_상한);
    }

    @Test
    @DisplayName("쫓겨난 항목은 사유 SIZE 로 알려 온다")
    void 사유는_SIZE(CapturedOutput output) {
      // 수업에서 Postman 으로 ?goalMinutes=100,200,…,700 을 부르는 것과 같은 모양이다.
      Cache 캐시 = cacheManager.getCache(CacheConfig.ACHIEVEMENT_RATE);
      for (int 목표 = 100; 목표 <= 700; 목표 += 100) {
        캐시.put(목표, 목표);
      }
      속을_꺼낸다(CacheConfig.ACHIEVEMENT_RATE).cleanUp();

      // 리스너는 ForkJoinPool.commonPool 에서 돌기 때문에 조금 늦게 도착한다.
      // await(...).until(...) 은 '이 조건이 참이 될 때까지 잠깐씩 다시 본다' 는 뜻이다
      // (Awaitility — 테스트 스타터에 딸려 온다. 고정 sleep 보다 덜 불안정하다).
      await().atMost(Duration.ofSeconds(3))
          .until(() -> output.getAll().contains("사유=SIZE"));
      assertThat(output.getAll()).contains("[캐시 제거]");
    }
  }

  @Nested
  @DisplayName("수명 — 시간이 지나면 사라진다")
  class 수명 {

    @Test
    @DisplayName("적어 넣은 지 수명이 지나면 없다고 답한다")
    void 지나면_사라진다() {
      Cache 캐시 = cacheManager.getCache(CacheConfig.ACTIVITY_SUMMARY);
      캐시.put("키", "값");
      assertThat(캐시.get("키")).isNotNull();

      await().atMost(Duration.ofSeconds(테스트_수명_초 + 3))
          .until(() -> 캐시.get("키") == null);
    }

    @Test
    @DisplayName("사유 EXPIRED — 아무도 지우라고 하지 않았는데 사라진다")
    void 사유는_EXPIRED(CapturedOutput output) {
      cacheManager.getCache(CacheConfig.ACHIEVEMENT_RATE).put(300, 40);

      // ⚠ 기다리기만 해서는 안 된다 — 만료된 항목을 실제로 치우는 일은
      //    '다음에 누가 캐시를 건드릴 때' 일어난다. 그래서 계속 두드려 본다.
      await().atMost(Duration.ofSeconds(테스트_수명_초 + 4)).until(() -> {
        속을_꺼낸다(CacheConfig.ACHIEVEMENT_RATE).cleanUp();
        return output.getAll().contains("사유=EXPIRED");
      });
      assertThat(output.getAll()).contains("키=300 사유=EXPIRED");
    }

    @Test
    @DisplayName("읽어도 수명은 연장되지 않는다 — expireAfterWrite 와 expireAfterAccess 의 차이")
    void write_와_access_는_다르다() throws Exception {
      var 쓴_시점부터 = Caffeine.newBuilder()
          .expireAfterWrite(Duration.ofMillis(1500)).build();
      var 만진_시점부터 = Caffeine.newBuilder()
          .expireAfterAccess(Duration.ofMillis(1500)).build();
      쓴_시점부터.put("키", "값");
      만진_시점부터.put("키", "값");

      // 0.5초마다 계속 읽어 준다 — 총 2.5초. 수명(1.5초)보다 짧은 간격이다.
      for (int i = 1; i <= 5; i++) {
        Thread.sleep(500);
        Object w = 쓴_시점부터.getIfPresent("키");
        Object a = 만진_시점부터.getIfPresent("키");
        System.out.printf("[관찰] t=%.1f초   write=%s   access=%s%n",
            i * 0.5, w == null ? "null" : w, a == null ? "null" : a);
      }

      assertThat(쓴_시점부터.getIfPresent("키")).isNull();        // 계속 읽어도 사라졌다
      assertThat(만진_시점부터.getIfPresent("키")).isEqualTo("값"); // 계속 읽으니 영원히 산다
    }
  }

  @Nested
  @DisplayName("제거 사유 — 손으로 비운 것과 시간이 비운 것은 다르다")
  class 사유 {

    @Test
    @DisplayName("@CacheEvict 로 비우면 사유가 EXPLICIT 이다")
    void 손으로_비우면_EXPLICIT(CapturedOutput output) {
      Cache 캐시 = cacheManager.getCache(CacheConfig.ACTIVITY_SUMMARY);
      캐시.put("키", "값");
      캐시.clear();     // @CacheEvict(allEntries = true) 가 부르는 바로 그 길

      await().atMost(Duration.ofSeconds(3))
          .until(() -> output.getAll().contains("사유=EXPLICIT"));
      // 쫓겨난 것이 아니라 우리가 지운 것이다
      assertThat(output.getAll()).contains("쫓겨난 것인가=false");
    }
  }

  @Nested
  @DisplayName("세는 기능 — 다음 시간에 볼 숫자의 자리를 만든다")
  class 센다 {

    private double 계기판(String 결과) {
      return meterRegistry.get("cache.gets")
          .tag("cache", CacheConfig.ACHIEVEMENT_RATE).tag("result", 결과)
          .functionCounter().count();
    }

    @Test
    @DisplayName("recordStats 를 켰으니 적중/빗나감 계기판이 생겼다")
    void 계기판이_생겼다() {
      // ⚠ 계기판 숫자는 앱이 뜬 뒤로 계속 더해지는 값이다(비우기로 0이 되지 않는다).
      //    그래서 '지금 값' 이 아니라 '내가 만든 만큼 늘었는가' 를 본다.
      double 적중_전 = 계기판("hit");
      double 빗나감_전 = 계기판("miss");

      Cache 캐시 = cacheManager.getCache(CacheConfig.ACHIEVEMENT_RATE);
      캐시.put(60, 50);
      캐시.get(60);
      캐시.get(60);
      캐시.get(999);   // 없는 키 — 빗나감

      meterRegistry.getMeters().stream()
          .map(m -> m.getId())
          .filter(id -> id.getName().startsWith("cache")
              && CacheConfig.ACHIEVEMENT_RATE.equals(id.getTag("cache")))
          .map(id -> id.getName() + (id.getTag("result") == null
              ? "" : "{result=" + id.getTag("result") + "}"))
          .sorted()
          .forEach(이름 -> System.out.println("[관찰] 미터 " + 이름));
      System.out.println("[관찰] hit  = " + (int) (계기판("hit") - 적중_전));
      System.out.println("[관찰] miss = " + (int) (계기판("miss") - 빗나감_전));

      assertThat(계기판("hit") - 적중_전).isEqualTo(2);
      assertThat(계기판("miss") - 빗나감_전).isEqualTo(1);
    }
  }
}
