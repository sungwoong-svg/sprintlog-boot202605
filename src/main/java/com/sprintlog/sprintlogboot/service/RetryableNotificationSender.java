package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.exception.NotificationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetryableNotificationSender {

  private final NotificationGateway gateway;

  @Retryable(retryFor = NotificationFailedException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 100, multiplier = 2))
  public void send(String channel, String message) {
    int attempt = RetrySynchronizationManager.getContext().getRetryCount() + 1;
    log.info("[@Retryable] {} - {}번째 시도", channel, attempt);
    gateway.send(channel, message);
  }

  @Recover
  public void recover(NotificationFailedException e, String channel, String message) {
    log.info("[@Recover] {} 발송을 포기한다 - 나중에 다시 보낼 목록에 담긴다. 내용={}, 사유={}", channel, message, e.getMessage());
  }

}
