package com.sprintlog.sprintlogboot.websocket;

import java.security.Principal;

// STOMP 연결 하나에 붙는 '누구인가'를 나타내는 객체
// CONNECT 때 토큰을 확인하고 한번 만들어 둔다. 그 뒤 이 연결로 오는 메시지 마다 스프링이 이 객체를 Principal로 넣어준다.
public record StompUser(Long id, String email, String nickname) implements Principal {

  @Override
  public String getName() {
    return email;
  }
}
