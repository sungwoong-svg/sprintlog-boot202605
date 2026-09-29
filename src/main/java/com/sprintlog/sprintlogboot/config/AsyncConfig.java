package com.sprintlog.sprintlogboot.config;

import java.util.concurrent.ThreadPoolExecutor;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

  private final AsyncExceptionHandler asyncExceptionHandler;

  @Override
  public @Nullable AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return asyncExceptionHandler;
  }

  // 알림 전용 풀
  @Bean("notificationExecutor")
  public ThreadPoolTaskExecutor notificationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);          // 평소 대기 인원
    executor.setMaxPoolSize(4);           // 바빠지면 여기까지
    executor.setQueueCapacity(4);         // 줄은 4칸 — 일부러 작게 잡아 포화를 눈으로 본다
    executor.setKeepAliveSeconds(60);     // core 를 넘겨 뽑은 일꾼이 놀면 60초 뒤 정리
    executor.setAllowCoreThreadTimeOut(false);   // core 2명은 항상 대기시킨다
    executor.setThreadNamePrefix("noti-");       // 로그에서 바로 알아보려고
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.initialize();
    return executor;
  }

  @Bean("dashboardExecutor")
  public ThreadPoolTaskExecutor dashboardExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);          // 평소 대기 인원
    executor.setMaxPoolSize(2);           // 바빠지면 여기까지
    executor.setQueueCapacity(50);         // 대시보드 관련 기능은 알림 기능보다는 외부 서버에 덜 의존적 -> 대기줄을 좀 더 넉넉하게 잡자.
    executor.setThreadNamePrefix("dash-");       // 로그에서 바로 알아보려고
    executor.initialize();
    return executor;
  }

}
