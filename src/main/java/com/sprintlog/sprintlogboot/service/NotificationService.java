package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.exception.NotificationFailedException;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationGateway gateway;

  public void sendBlocking(String channel, String message) {
    log.info("[동기] {} - 일꾼 {}", channel, Thread.currentThread().getName());
    gateway.send(channel, message);
  }

  @Async("notificationExecutor")
  public void sendAsync(String channel, String message) {
    log.info("[비동기 void] {} - 일꾼 {}", channel, Thread.currentThread().getName());
    gateway.send(channel, message);
  }

  @Async("notificationExecutor")
  public CompletableFuture<String> sendAsyncWithResult(String channel, String message) {
    log.info("[비동기 결과] {} - 일꾼 {}", channel, Thread.currentThread().getName());
    gateway.send(channel, message);
    return CompletableFuture.completedFuture(channel + "발송 완료");
  }

  @Async("notificationExecutor")
  public void sendAsyncThatFails(String channel, String message) {
    log.info("[비동기 예외] 알림 실패 {} - 일꾼 {}", channel, Thread.currentThread().getName());
    throw new NotificationFailedException(channel + "발송 실패(흉내)");
  }

  @Async("notificationExecutor")
  public CompletableFuture<String> sendAsyncWithResultThatFails(String channel, String message) {
    log.info("[비동기 실패, 결과] {} - 일꾼 {}", channel, Thread.currentThread().getName());
    throw new NotificationFailedException(channel + "발송 실패(흉내)");
  }

  @Async("notificationExecutor")
  public void sendAsyncWithWho(String channel) {
    var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    String who = (auth != null) ? auth.getName() : "(모름)";
    boolean inTx = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();

    log.info("[비동기·누가] {} — 요청자 {} · 트랜잭션 {} · 일꾼 {}",
        channel, who, inTx ? "있음" : "없음", Thread.currentThread().getName());
  }

  public void sendViaSelfCall(String channel, String message) {
    log.info("[자가 호출] 부른 쪽 일꾼 {}", Thread.currentThread().getName());
    this.sendAsync(channel, message);
  }

}
