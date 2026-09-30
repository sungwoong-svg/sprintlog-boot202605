package com.sprintlog.sprintlogboot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintlog.sprintlogboot.config.CacheConfig;
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
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * 스프링이 캐시를 감추는 방식 — 스위치 · 두 인터페이스 · 프록시 · 키.
 *
 * <p>여기 적힌 숫자와 이름은 전부 실제로 돌려서 확인한 것이다.
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("스프링이 캐시를 감춘다")
class CacheBasicsTest {

  @Autowired ApplicationContext context;
  @Autowired CacheManager cacheManager;
  @Autowired CachedDashboardService cached;

  /**
   * ⚠ 캐시는 스프링 컨텍스트에 붙어 있고, 컨텍스트는 테스트 클래스끼리 <b>재사용</b>된다.
   * 비우지 않으면 앞 테스트가 적어 둔 값이 다음 테스트로 새어 들어간다.
   */
  @BeforeEach
  void clearCaches() {
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  @Nested
  @DisplayName("스위치를 켜면 무엇이 생기는가")
  class 스위치 {

    @Test
    @DisplayName("CacheManager 빈이 하나 있고, 그 구현체는 클래스패스가 정한다")
    void 기본_구현체() {
      String[] names = context.getBeanNamesForType(CacheManager.class);
      System.out.println("[관찰] CacheManager 빈 수 = " + names.length);
      System.out.println("[관찰]   " + names[0] + " -> " + cacheManager.getClass().getName());

      assertThat(names).hasSize(1);
      // 이 단언은 원래 ConcurrentMapCacheManager 였다.
      //   우리 코드는 한 줄도 안 바뀌었는데 의존성 두 줄이 늘자 여기가 빨간불이 됐다 —
      //   그 빨간불 자체가 '구현체가 갈렸다' 는 증거다.
      //   @Cacheable 을 쓰는 쪽은 아무것도 몰라도 된다는 것이 추상화의 값이다.
      assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
    }

    @Test
    @DisplayName("@EnableCaching 은 캐시 매니저만이 아니라 '프록시 부품' 도 같이 만든다")
    void 프록시_부품() {
      // ⚠ 'Caching' 만으로 거르면 캐시와 무관한 internalCachingMetadataReaderFactory 가 섞인다.
      for (String n : context.getBeanDefinitionNames()) {
        if (n.contains("org.springframework.cache") || n.equals("cacheManager")
            || n.equals("cacheInterceptor") || n.equals("cacheOperationSource")
            || n.contains("autoconfigure.cache.Simple")
            || n.contains("metrics.cache.CacheMetricsAutoConfiguration")) {
          System.out.println("[관찰] 같이 생긴 빈 — " + n);
        }
      }
      // 이 빈이 있어서 부트의 캐시 자동 설정이 켜진다(@ConditionalOnBean(CacheAspectSupport))
      assertThat(context.getBeanNamesForType(CacheInterceptor.class)).hasSize(1);
      assertThat(context.containsBean("cacheOperationSource")).isTrue();
    }
  }

  @Nested
  @DisplayName("손으로 쓴 캐시 — CacheManager 와 Cache 두 인터페이스만 쓴다")
  class 손으로 {

    @Test
    @DisplayName("두 번째 호출은 원본을 부르지 않는다")
    void 두번째는_원본을_안_부른다(CapturedOutput output) {
      cached.summarize();
      cached.summarize();

      // 원본이 돌면 AOP 차시에서 만든 ⏰ 로그가 찍힌다 — 그게 한 번뿐이어야 한다.
      // ⚠ "⏰ " 까지 붙여서 센다. 같은 메서드 이름이 AdviceTypesAspect 의 [@Before] 줄에도 찍히기 때문이다.
      assertThat(countOf(output, "⏰ ActivityDashboard.summarize()")).isEqualTo(1);
      assertThat(output).contains("[캐시 미스]").contains("[캐시 히트]");
    }

    @Test
    @DisplayName("비우면 다시 부른다")
    void 비우면_다시_부른다(CapturedOutput output) {
      cached.summarize();
      cached.evict();
      cached.summarize();

      assertThat(countOf(output, "⏰ ActivityDashboard.summarize()")).isEqualTo(2);
    }

    @Test
    @DisplayName("캐시가 돌려준 값은 원본과 같은 내용이다")
    void 같은_값() {
      ActivityDashboard.Summary first = cached.summarize();
      ActivityDashboard.Summary second = cached.summarize();

      assertThat(second.getTotalCount()).isEqualTo(first.getTotalCount());
    }
  }

  @Nested
  @DisplayName("이름과 키 — 문자열이라 컴파일러가 안 잡아 준다")
  class 이름과키 {

    @Test
    @DisplayName("이름을 안 적어 두면, 오타를 내도 그 자리에서 새 캐시가 생긴다")
    void 이름을_안_적으면_오타가_조용히_통과한다() {
      // 설정에 이름을 안 적었을 때의 기본 동작 — 우리 빈이 아니라 같은 클래스를 직접 만들어 본다
      ConcurrentMapCacheManager 안_적어_둔_경우 = new ConcurrentMapCacheManager();

      assertThat(안_적어_둔_경우.getCacheNames()).isEmpty();

      System.out.println("[관찰] (안 적어 둔 경우) 처음 이름들 = " + 안_적어_둔_경우.getCacheNames());
      Cache typo = 안_적어_둔_경우.getCache("activitySummaryy"); // 오타
      System.out.println("[관찰] (안 적어 둔 경우) 오타로 부른 뒤 = " + 안_적어_둔_경우.getCacheNames());

      assertThat(typo).isNotNull();                                          // 예외가 안 난다
      assertThat(안_적어_둔_경우.getCacheNames()).contains("activitySummaryy"); // 생겨 버렸다
    }

    @Test
    @DisplayName("설정에 이름을 적어 두면, 목록에 없는 이름은 곧바로 없다고 답한다")
    void 이름을_적어_두면_오타가_걸린다() {
      // 우리 프로젝트는 application.yml 에 이름을 적어 뒀다
      // ⚠ 캐시 이름이 하나 더 늘었다(achievementRate) — 그래서 "정확히 하나" 가 아니라 "포함" 으로 본다.
      System.out.println("[관찰] (적어 둔 경우) 이름들 = " + cacheManager.getCacheNames());
      System.out.println("[관찰] (적어 둔 경우) 오타로 부르면 = " + cacheManager.getCache("activitySummaryy"));

      assertThat(cacheManager.getCacheNames()).contains(CacheConfig.ACTIVITY_SUMMARY);

      assertThat(cacheManager.getCache("activitySummaryy")).isNull(); // 오타 → 없다
    }

    @Test
    @DisplayName("스프링의 기본 키 규칙 — 인자 0개는 전부 같은 칸, 1개면 그 인자 자체")
    void 기본_키_규칙() {
      System.out.println("[관찰] 인자 0개 → " + SimpleKeyGenerator.generateKey());
      System.out.println("[관찰] 인자 1개 → " + SimpleKeyGenerator.generateKey(7L)
          + "  (" + SimpleKeyGenerator.generateKey(7L).getClass().getSimpleName() + ")");
      System.out.println("[관찰] 인자 2개 → " + SimpleKeyGenerator.generateKey(7L, "a")
          + "  (" + SimpleKeyGenerator.generateKey(7L, "a").getClass().getSimpleName() + ")");
      System.out.println("[관찰] 무인자 키 두 번이 같은가 = "
          + SimpleKeyGenerator.generateKey().equals(SimpleKeyGenerator.generateKey()));

      // 인자가 없으면 SimpleKey.EMPTY — 호출을 몇 번 하든 같은 키다
      assertThat(SimpleKeyGenerator.generateKey()).isEqualTo(SimpleKey.EMPTY);
      assertThat(SimpleKeyGenerator.generateKey()).isEqualTo(SimpleKeyGenerator.generateKey());

      // 인자가 하나면 그 인자가 곧 키다 (감싸지 않는다)
      assertThat(SimpleKeyGenerator.generateKey(7L)).isEqualTo(7L);

      // ⚠ 예외 둘 — 인자가 하나여도 null 이거나 배열이면 감싼다
      System.out.println("[관찰] 인자 1개인데 null → " + SimpleKeyGenerator.generateKey((Object) null));
      System.out.println("[관찰] 인자 1개인데 배열 → " + SimpleKeyGenerator.generateKey((Object) new int[]{1, 2}));
      assertThat(SimpleKeyGenerator.generateKey((Object) null)).isInstanceOf(SimpleKey.class);
      assertThat(SimpleKeyGenerator.generateKey((Object) new int[]{1, 2})).isInstanceOf(SimpleKey.class);

      // 둘 이상이면 SimpleKey 로 묶는다
      assertThat(SimpleKeyGenerator.generateKey(7L, "a")).isInstanceOf(SimpleKey.class);
      assertThat(SimpleKeyGenerator.generateKey(7L, "a"))
          .isEqualTo(SimpleKeyGenerator.generateKey(7L, "a")); // 값이 같으면 같은 키
    }

    @Test
    @DisplayName("null 도 저장된다 — '아직 안 넣었다' 와 'null 이 답이다' 는 다르다")
    void null_도_저장된다() {
      Cache cache = cacheManager.getCache(CacheConfig.ACTIVITY_SUMMARY);

      assertThat(cache.get("없는키")).isNull();           // 아직 안 넣었다

      cache.put("널키", null);

      assertThat(cache.get("널키")).isNotNull();          // 넣긴 넣었고
      assertThat(cache.get("널키").get()).isNull();       // 그 값이 null 이다
    }
  }

  private static int countOf(CapturedOutput output, String needle) {
    int count = 0, from = 0;
    String all = output.getAll();
    while ((from = all.indexOf(needle, from)) >= 0) {
      count++;
      from += needle.length();
    }
    return count;
  }
}