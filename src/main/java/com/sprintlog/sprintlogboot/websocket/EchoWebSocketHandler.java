package com.sprintlog.sprintlogboot.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
public class EchoWebSocketHandler extends TextWebSocketHandler {

  // 핸드셰이크가 끝나고 통로가 열린 순간 한 번 불린다.
  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    log.info("[WS] 연결됨 - 세션 {}. 일꾼 {}", session.getId(), Thread.currentThread().getName());
  }

  // 브라우저가 보낸 글자 프레임 하나가 도착할 때마다 불린다.
  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    String payload = message.getPayload();
    log.info("[WS] 받음 - 세션 {}, \"{}\"", session.getId(), payload);
    // 같은 통로로 바로 돌려보낸다. 요청-응답이 아니라 서버가 아무때나 보낼 수 있는 통로
    session.sendMessage(new TextMessage("받았다: " + payload));
  }

  // 어느 쪽이든 연결이 닫히면 불린다. 닫힌 이유가 함께 온다.
  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    log.info("[WS] 닫힘 - 세션 {}, {}", session.getId(), status.getReason());
  }
}
