package com.sprintlog.sprintlogboot.websocket;

import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.repository.StudyRoomRepository;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import com.sprintlog.sprintlogboot.security.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

  private static final String BEARER_PREFIX = "Bearer ";
  /** 구독해도 되는 방 주소 — /topic/rooms.{번호} 와 /topic/rooms.{번호}.presence */
  private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/rooms\\.(\\d+)(\\.presence)?$");
  private static final String MY_ERRORS = "/user/queue/errors";

  private final JwtProvider jwtProvider;
  private final UserRepository userRepository;
  private final StudyRoomRepository studyRoomRepository;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() == null) {
      return message;   // 하트비트 등 — STOMP 명령이 아닌 것은 그냥 통과
    }
    switch (accessor.getCommand()) {
      case CONNECT, STOMP -> accessor.setUser(authenticate(accessor));
      case SUBSCRIBE -> {
        requireUser(accessor);
        checkSubscribe(accessor.getDestination());
      }
      case SEND -> {
        requireUser(accessor);
        checkSend(accessor.getDestination());
      }
      default -> { }
    }
    return message;
  }

  /** CONNECT 의 Authorization 헤더 → 토큰 검증 → 사용자 확인 → StompUser. */
  private StompUser authenticate(StompHeaderAccessor accessor) {
    String header = accessor.getFirstNativeHeader("Authorization");
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      throw new BadCredentialsException("토큰이 없습니다");
    }
    Claims claims;
    try {
      claims = jwtProvider.parseClaims(header.substring(BEARER_PREFIX.length()));
    } catch (ExpiredJwtException e) {
      throw new BadCredentialsException("토큰이 만료되었습니다");
    } catch (JwtException | IllegalArgumentException e) {
      throw new BadCredentialsException("유효하지 않은 토큰입니다");
    }
    // 연결은 길게 산다 — 연결할 때 한 번은 DB 에서 사람을 확인한다(탈퇴한 사용자의 토큰을 여기서 막는다).
    Long userId = jwtProvider.getUserId(claims);
    User user = (userId == null ? null : userRepository.findById(userId).orElse(null));
    if (user == null) {
      throw new BadCredentialsException("사용자를 찾을 수 없습니다");
    }
    log.info("[STOMP] 연결 인증 — {} · 세션 {}", user.getNickname(), accessor.getSessionId());
    return new StompUser(user.getId(), user.getEmail(), user.getNickname());
  }

  /**
   * 이름표가 없으면 거절 — 보험.
   * 지금 스프링은 CONNECT 전에 온 프레임이나 두 번째 CONNECT 를 스스로 막는다(화면엔 '요청을 처리할 수 없습니다').
   * 그래도 버전이 바뀌거나 새 길이 생겨도 버티도록 한 번 더 본다.
   */
  private void requireUser(StompHeaderAccessor accessor) {
    if (!(accessor.getUser() instanceof StompUser)) {
      throw new BadCredentialsException("먼저 토큰으로 연결해야 합니다");
    }
  }

  private void checkSubscribe(String destination) {
    if (MY_ERRORS.equals(destination)) {
      return;
    }
    Matcher m = destination == null ? null : ROOM_TOPIC.matcher(destination);
    if (m == null || !m.matches()) {
      throw new AccessDeniedException("구독할 수 없는 주소입니다 (" + destination + ")");
    }
    if (!studyRoomRepository.existsById(Long.valueOf(m.group(1)))) {
      throw new AccessDeniedException("없는 방입니다 (" + destination + ")");
    }
  }

  private void checkSend(String destination) {
    if (destination == null || !destination.startsWith("/app/")) {
      throw new AccessDeniedException("보낼 수 없는 주소입니다 (" + destination + ")");
    }
  }
}
