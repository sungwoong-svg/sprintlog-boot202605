package com.sprintlog.sprintlogboot.security;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.session.HttpSessionCreatedEvent;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SessionEventListener {

  private static final DateTimeFormatter FORMATTER
      = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
      .withZone(ZoneId.systemDefault());

  @EventListener
  public void onSessionCreated(HttpSessionCreatedEvent event) {
    HttpSession session = event.getSession();

    log.info("=== 새 세션 생성 ===");
    log.info("세션 ID: {}", session.getId());
    log.info("생성 시간: {}", FORMATTER.format(Instant.ofEpochMilli(session.getCreationTime())));
    log.info("최대 비활성 시간: {}분", session.getMaxInactiveInterval() / 60);
  }

  @EventListener
  public void onSessionDestroyed(HttpSessionDestroyedEvent event) {
    HttpSession session = event.getSession();

    log.info("=== 세션 소멸 ===");
    log.info("세션 ID: {}", session.getId());

    // 세션이 소멸되는 시점에는 이미 SecurityContextHolder가 비어있기 때문에
    // 세션에 저장된 속성에서 직접 꺼냅니다.
    SecurityContext context
        = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);

    if (context != null && context.getAuthentication() != null) {
      log.info("사용자: {}", context.getAuthentication().getName());
      log.info("로그아웃 시간: {}", Instant.now());
    }
  }

}
