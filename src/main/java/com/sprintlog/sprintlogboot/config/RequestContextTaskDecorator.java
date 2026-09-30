package com.sprintlog.sprintlogboot.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// 일을 다른 일꾼에게 넘길 때 따라가지 않는 것들을 직접 들려 보내는 역할.
@Component
public class RequestContextTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable task) {
    // 1. 맡기는 곳에서 넘기고자 하는 데이터를 준비한다.
    Map<String, String> callerMdc = MDC.getCopyOfContextMap();
    SecurityContext callerSecurity = SecurityContextHolder.getContext();

    return () -> {
      // 2. 여기부터가 일꾼 스레드 -> 준비된 데이터를 세팅한다.
      if (callerMdc != null) {
        MDC.setContextMap(callerMdc);
      }
      SecurityContextHolder.setContext(callerSecurity);

      try {
        task.run();
      } finally {
        // 3. 반드시 지운다. 이걸 빠트리면 다음 일이 남의 번호, 남의 로그인 정보로 돌아간다.
          MDC.clear();
          SecurityContextHolder.clearContext();
      }
    };
  }
}
