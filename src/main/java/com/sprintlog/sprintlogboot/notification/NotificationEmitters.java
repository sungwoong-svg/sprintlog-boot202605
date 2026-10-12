package com.sprintlog.sprintlogboot.notification;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
public class NotificationEmitters {

  private final Map<Long, List<SseEmitter>> byUser = new ConcurrentHashMap<>();
  private final AtomicLong eventIds = new AtomicLong();

  private final Duration timeout;

  public NotificationEmitters(@Value("${sprintlog.notifications.stream-timeout:30m}") Duration timeout) {
    this.timeout = timeout;
  }

  /** 연결 하나를 연다 — 장부에 올리고, '연결됨' 을 먼저 보낸다. */
  public SseEmitter connect(Long userId) {
    SseEmitter emitter = new SseEmitter(timeout.toMillis());
    byUser.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>()).add(emitter);
    emitter.onCompletion(() -> remove(userId, emitter));
    // 시한이 되면 '정상 종료' 로 닫는다. 이 줄이 없으면 스프링이 시한 초과 예외를 던져
    //   우리의 마지막 그물(GlobalExceptionHandler)이 ERROR 로 받는다 — 매 30분마다, 사람마다.
    emitter.onTimeout(emitter::complete);
    emitter.onError(e -> remove(userId, emitter));

    try {
      emitter.send(SseEmitter.event().name("connected").data(Map.of("message", "알림 연결됨")));
    } catch (IOException e) {
      remove(userId, emitter);
    }
    log.info("[알림 연결] 열림 — 사용자 {} · 열린 연결 {}개 · 일꾼 {}", userId, count(userId), Thread.currentThread().getName());
    return emitter;
  }

  /** 이 사용자의 열린 연결 모두에 이벤트 하나를 보낸다. 보낸 연결 수를 돌려준다. */
  public int send(Long userId, String eventName, Object data) {
    List<SseEmitter> emitters = byUser.getOrDefault(userId, List.of());
    if (emitters.isEmpty()) {
      log.info("[알림 연결] 열린 연결 없음 — 사용자 {} · '{}' 은 화면으로 못 보낸다", userId, eventName);
      return 0;
    }
    String id = String.valueOf(eventIds.incrementAndGet());
    int sent = 0;
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().id(id).name(eventName).data(data));
        sent++;
      } catch (IOException | IllegalStateException e) {
        remove(userId, emitter);   // 이미 떠난 연결 — 장부에서 뺀다
      }
    }
    log.info("[알림 연결] 보냄 — 사용자 {} · '{}' · {}개 연결 · 일꾼 {}", userId, eventName, sent, Thread.currentThread().getName());
    return sent;
  }

  public int count(Long userId) {
    return byUser.getOrDefault(userId, List.of()).size();
  }

  private void remove(Long userId, SseEmitter emitter) {
    List<SseEmitter> emitters = byUser.get(userId);
    if (emitters != null && emitters.remove(emitter)) {
      log.info("[알림 연결] 연결 정리 — 사용자 {} · 남은 연결 {}개", userId, emitters.size());
    }
  }
}
