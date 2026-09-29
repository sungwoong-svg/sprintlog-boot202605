package com.sprintlog.sprintlogboot.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

/**
 * 손으로 짠 재시도(ReliableNotificationSender)를 애너테이션 한 줄로 — @Retryable · @Recover.
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("@Retryable — 재시도를 애너테이션으로")
class RetryableTest {

  @Autowired private RetryableNotificationSender sender;
  @Autowired private NotificationGateway gateway;

  @AfterEach
  void resetGateway() {
    gateway.simulateFailures(0);
  }

  @Test
  @DisplayName("두 번 실패해도 세 번째에 성공한다 — for 문 없이")
  void 세_번째에_성공(CapturedOutput output) {
    gateway.simulateFailures(2);

    sender.send("이메일", "@Retryable 관찰");

    System.out.println("[관찰] 시도 기록 = " + output.toString().lines()
        .filter(l -> l.contains("[@Retryable]")).map(l -> l.substring(l.indexOf("[@Retryable]"))).toList());
    assertThat(output).contains("[@Retryable] 이메일 - 3번째 시도").contains("[알림] 이메일 발송 완료");
    assertThat(output).doesNotContain("[@Recover]");
  }

  @Test
  @DisplayName("세 번 다 실패하면 @Recover 로 간다 — 예외는 부른 쪽으로 안 올라온다")
  void 다_실패하면_Recover(CapturedOutput output) {
    gateway.simulateFailures(5);

    sender.send("이메일", "포기 관찰");   // 예외가 올라오지 않는다 — @Recover 가 받았다

    System.out.println("[관찰] 폴백 = " + output.toString().lines()
        .filter(l -> l.contains("[@Recover]")).map(l -> l.substring(l.indexOf("[@Recover]"))).findFirst().orElse("(없음)"));
    assertThat(output).contains("[@Retryable] 이메일 — 3번째 시도").contains("[@Recover] 이메일 발송을 포기한다");
    assertThat(output).doesNotContain("4번째 시도");
  }
}