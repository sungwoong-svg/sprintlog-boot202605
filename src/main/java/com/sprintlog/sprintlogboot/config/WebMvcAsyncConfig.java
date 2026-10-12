package com.sprintlog.sprintlogboot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcAsyncConfig implements WebMvcConfigurer {

  private final ObjectProvider<RequestContextTaskDecorator> contextDecorator;

  @Override
  public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    configurer.setTaskExecutor(mvcAsyncExecutor());
  }

  @Bean("mvcAsyncExecutor")
  public ThreadPoolTaskExecutor mvcAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("mvc-");
    contextDecorator.ifAvailable(executor::setTaskDecorator);
    executor.initialize();
    return executor;
  }
}
