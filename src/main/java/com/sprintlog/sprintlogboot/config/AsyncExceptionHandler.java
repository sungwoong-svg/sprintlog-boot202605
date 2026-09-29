package com.sprintlog.sprintlogboot.config;

import com.sprintlog.sprintlogboot.exception.NotificationFailedException;
import java.lang.reflect.Method;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.stereotype.Component;

// @Async void 메서드에서 터진 예외를 마지막으로 받아주는 곳
// CompletableFuture를 반환하는 메서드는 여기 오지 않는다. -> 걔는 표 안에 담겨져서 부르는 쪽으로 간다.
@Component
@Slf4j
public class AsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

  @Override
  public void handleUncaughtException(Throwable ex, Method method, Object... params) {

    // 예외 종류마다 대응이 다르다 — 전부 같은 급으로 찍으면 진짜 급한 것이 묻힌다.
    if (ex instanceof NotificationFailedException) {
      log.warn("[비동기 예외] 알림 실패(다시 걸어 볼 만하다) — {}(...) 인자={} 사유={}",
          method.getName(), Arrays.toString(params), ex.getMessage());
      return;
    }

    log.error("[비동기 예외] ⚠ 예상 못 한 실패 — {}(...) 인자={}",
        method.getName(), Arrays.toString(params), ex);

  }
}
