package com.sprintlog.sprintlogboot.notification;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class StreamTicketService {

  public static final Duration TTL = Duration.ofSeconds(30);

  private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();
  private final Clock clock;

  public StreamTicketService(Clock clock) {
    this.clock = clock;
  }

  /** 이 사용자에게 입장권 한 장. */
  public String issue(Long userId) {
    Instant now = clock.instant();
    tickets.values().removeIf(t -> t.expiresAt().isBefore(now));   // 쓰이지 않고 지난 것 정리
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    tickets.put(ticket, new Ticket(userId, now.plus(TTL)));
    return ticket;
  }

  /** 입장권을 쓴다 — 맞으면 사용자 id, 없거나·이미 썼거나·지났으면 빈 값. 한 번 쓰면 사라진다. */
  public Optional<Long> consume(String ticket) {
    if (ticket == null) {
      return Optional.empty();
    }
    Ticket found = tickets.remove(ticket);   // 꺼내는 동시에 지운다 — 같은 입장권으로 두 번 못 들어온다
    if (found == null || found.expiresAt().isBefore(clock.instant())) {
      return Optional.empty();
    }
    return Optional.of(found.userId());
  }

  private record Ticket(Long userId, Instant expiresAt) {}
}
