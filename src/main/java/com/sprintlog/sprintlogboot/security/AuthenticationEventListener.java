package com.sprintlog.sprintlogboot.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthenticationEventListener {

  @EventListener
  public void onSuccess(AuthenticationSuccessEvent event) {
    log.info("[AUTH] 인증 성공 - 사용자: {}", event.getAuthentication().getName());
  }

  @EventListener
  public void onFailure(AbstractAuthenticationFailureEvent event) {
    log.warn("[AUTH] 인증 실패 - 시도한 아이디: {}, 이유: {}",
        event.getAuthentication().getName(),
        event.getException().getMessage());

    // 특정 실패 유형별 처리
    if (event instanceof AuthenticationFailureBadCredentialsEvent) {
      // 비밀번호 오류 (실패 회숫 체크해서 게정 ㅈ막므 로직 가능)
      log.warn("비밀번호 오류");
    } else if (event instanceof AuthenticationFailureDisabledEvent) {
      log.warn("비활성화된 계정");
    }
  }

}
