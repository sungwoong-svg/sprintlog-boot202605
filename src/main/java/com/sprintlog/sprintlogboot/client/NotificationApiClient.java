package com.sprintlog.sprintlogboot.client;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationApiClient {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private final WebClient slowApiClient;

  public Mono<Map<String, Object>> send(String channel, String message, long serverDelayMs) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("channel", channel);
    body.put("message", message);

    return slowApiClient.post()
        .uri(uriBuilder -> uriBuilder.path("/notifications").queryParam("ms", serverDelayMs).build())
        .bodyValue(body)                                   // ⭐ 보내는 호출에만 있는 단계
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
        .timeout(TIMEOUT)
        .doOnNext(result -> log.info("[알림 발송] {} — {}", channel, result))
        .onErrorResume(e -> {
          log.warn("[알림 발송] {} 실패 — {} : {}", channel, e.getClass().getSimpleName(), e.getMessage());
          Map<String, Object> fallback = new LinkedHashMap<>();
          fallback.put("status", "발송 실패");
          fallback.put("channel", channel);
          return Mono.just(fallback);                    // 폴백 — 예외를 밖으로 새지 않게 한다
        });
  }
}
