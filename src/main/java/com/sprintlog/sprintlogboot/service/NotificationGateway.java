package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.exception.NotificationFailedException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotificationGateway {

  /** 외부 게이트웨이 응답을 기다리는 시간. 일부러 느리게 잡았다. */
  private static final long SEND_MS = 3_000;

  /**
   * ⚠ 수업·테스트용 고장 스위치.
   *
   * 실제 게이트웨이는 우리가 고장 낼 수 없다. 그런데 **실패를 재현하지 못하면 대응을 짤 수도 없다.**
   * 그래서 "다음 N번은 실패한다" 를 흉내 낼 수 있게 열어 둔다.
   */
  private final AtomicInteger remainingFailures = new AtomicInteger(0);

  public void simulateFailures(int count) {
    remainingFailures.set(count);
  }

  public void send(String channel, String message) {
    // 실패는 '기다리기 전' 에 낸다 — 재시도를 눈으로 보려면 실패가 빨라야 한다.
    if (remainingFailures.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
      log.warn("[알림] {} 발송 실패 — 게이트웨이가 거절했다", channel);
      throw new NotificationFailedException(channel + " 발송 실패");
    }

    log.info("[알림] {} 발송 시작 — {}", channel, message);
    try {
      Thread.sleep(SEND_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.info("[알림] {} 발송 중단됨", channel);
      return;
    }
    log.info("[알림] {} 발송 완료", channel);
  }

}
