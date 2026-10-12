package com.sprintlog.sprintlogboot.notification;

import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.dto.response.StreamTicketResponse;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationStreamController {

  private final StreamTicketService tickets;
  private final UserRepository userRepository;
  private final NotificationEmitters emitters;

  @PostMapping("/stream-ticket")
  public ResponseEntity<StreamTicketResponse> issueTicket(Authentication authentication) {
    Optional<User> user = userRepository.findByEmail(authentication.getName());
    if (user.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String ticket = tickets.issue(user.get().getId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new StreamTicketResponse(ticket, StreamTicketService.TTL.toSeconds()));
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> stream(@RequestParam(required = false) String ticket) {
    Optional<Long> userId = tickets.consume(ticket);
    if (userId.isEmpty()) {
      log.info("[알림 연결] 입장권 거절 - 없거나, 이미 썼거나, 지났다.");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return ResponseEntity.ok(emitters.connect(userId.get()));
  }
}
