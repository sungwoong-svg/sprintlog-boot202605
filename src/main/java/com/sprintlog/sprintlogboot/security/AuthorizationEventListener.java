package com.sprintlog.sprintlogboot.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthorizationEventListener {

  @EventListener
  public void onDenied(AuthorizationDeniedEvent<?> event) {
    String who = event.getAuthentication().get() != null
        ? event.getAuthentication().get().getName()
        : "anonymous";

    log.warn("[AUTHZ} 인가 거부 - 사용자: {}, 결정: {}", who, event.getAuthorizationResult());

  }

}
