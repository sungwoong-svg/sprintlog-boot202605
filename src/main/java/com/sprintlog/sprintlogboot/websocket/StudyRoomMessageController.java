package com.sprintlog.sprintlogboot.websocket;

import com.sprintlog.sprintlogboot.websocket.StudyRoomMessages.ChatRequest;
import com.sprintlog.sprintlogboot.websocket.StudyRoomMessages.Presence;
import com.sprintlog.sprintlogboot.websocket.StudyRoomMessages.RoomMessage;
import com.sprintlog.sprintlogboot.websocket.StudyRoomMessages.StatusRequest;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class StudyRoomMessageController {

  private final SimpMessagingTemplate messaging;
  private final StudyRoomPresence presence;

  @MessageMapping("/rooms/{roomId}/join")
  public void join(
      @DestinationVariable Long roomId,
      SimpMessageHeaderAccessor headers,
      Principal principal
  ) {
    String sessionId = headers.getSessionId();
    String nickname = nicknameOf(principal, sessionId);
    presence.join(roomId, sessionId, nickname);
    log.info("[STOMP] 입장 - 방 {}, {}, 일꾼 {}", roomId, nickname, Thread.currentThread().getName());
    broadcast(roomId, RoomMessage.of("JOIN", roomId, nickname, nickname + " 님이 들어왔어요", null));
    broadcastPresence(roomId);
  }

  @MessageMapping("/rooms/{roomId}/chat")
  public void chat(@DestinationVariable Long roomId, ChatRequest request,
      SimpMessageHeaderAccessor headers, Principal principal) {
    String nickname = nicknameOf(principal, headers.getSessionId());
    log.info("[STOMP] 채팅 — 방 {} · {} · \"{}\"", roomId, nickname, request.content());
    broadcast(roomId, RoomMessage.of("CHAT", roomId, nickname, request.content(), null));
  }

  @MessageMapping("/rooms/{roomId}/status")
  public void status(@DestinationVariable Long roomId, StatusRequest request,
      SimpMessageHeaderAccessor headers, Principal principal) {
    String sessionId = headers.getSessionId();
    String nickname = nicknameOf(principal, sessionId);
    presence.updateStatus(roomId, sessionId, request.studying());
    broadcast(roomId, RoomMessage.of("STATUS", roomId, nickname, null, request.studying()));
    broadcastPresence(roomId);
  }

  @MessageMapping("/rooms/{roomId}/leave")
  public void leave(@DestinationVariable Long roomId, SimpMessageHeaderAccessor headers, Principal principal) {
    String nickname = presence.leave(roomId, headers.getSessionId());
    if (nickname == null) {
      return;
    }
    log.info("[STOMP] 퇴장 — 방 {} · {}", roomId, nickname);
    broadcast(roomId, RoomMessage.of("LEAVE", roomId, nickname, nickname + " 님이 나갔어요", null));
    broadcastPresence(roomId);
  }

  // 접속자 목록을 방송 (채팅창 내)
  private void broadcastPresence(Long roomId) {
    messaging.convertAndSend("/topic/rooms." + roomId + ".presence"
        , new Presence(roomId, presence.members(roomId)));
  }

  // 메시지 내용을 발송
  private void broadcast(Long roomId, RoomMessage message) {
    messaging.convertAndSend("/topic/rooms." + roomId, message);
  }

  private String nicknameOf(Principal principal, String sessionId) {
    if (principal != null) {
      return principal.getName();
    }
    return "손님-" + sessionId.substring(0, Math.min(4, sessionId.length()));
  }

}
