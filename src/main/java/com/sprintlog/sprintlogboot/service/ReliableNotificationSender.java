package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.exception.NotificationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReliableNotificationSender {

  /** 몇 번까지 다시 걸어 볼 것인가. */
  private static final int MAX_ATTEMPTS = 3;

  /** 재시도 사이 기본 간격(ms). 시도 횟수만큼 곱해 늘린다 — 상대가 숨 돌릴 틈을 준다. */
  private static final long BASE_BACKOFF_MS = 100;

  private final NotificationGateway gateway;

  @Async("notificationExecutor")
  public void sendWithRetry(String channel, String message) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        gateway.send(channel, message);
        if (attempt > 1) {
          log.info("[재시도] {} — {}번째 시도에서 성공", channel, attempt);
        }
        return;
      } catch (NotificationFailedException e) {
        log.warn("[재시도] {} — {}/{} 실패: {}", channel, attempt, MAX_ATTEMPTS, e.getMessage());

        if (attempt == MAX_ATTEMPTS) {
          fallback(channel, message);
          return;
        }
        // 100 → 200 → 300 … 시도할수록 간격을 벌린다(선형).
        // 실무에선 배수로 키우는 '지수 백오프' 가 더 흔하고, 거기에 약간의 무작위(지터)를 섞는다.
        sleepQuietly(BASE_BACKOFF_MS * attempt);
      }
    }
  }

  /**
   * 세 번 다 실패했을 때의 다른 길.
   *
   * ⚠ 폴백은 '성공' 이 아니다 — "실패했다는 사실을 잃어버리지 않는 것" 이 목적이다.
   *   실무에서는 여기서 다른 채널로 보내거나, 나중에 다시 보낼 큐에 적어 둔다.
   */
  private void fallback(String channel, String message) {
    log.error("[폴백] {} 발송을 포기한다 — 나중에 다시 보낼 목록에 남긴다. 내용={}", channel, message);
  }

  private static void sleepQuietly(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

}
