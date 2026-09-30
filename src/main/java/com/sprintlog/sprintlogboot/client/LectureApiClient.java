package com.sprintlog.sprintlogboot.client;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Component
@Slf4j
@RequiredArgsConstructor
public class LectureApiClient {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private final WebClient slowApiClient;

  public Mono<Map<String, Object>> fetchLecture(long id, long serverDelayMs) {
    return slowApiClient.get()
        .uri(uriBuilder -> uriBuilder.path("/lectures/{id}").queryParam("ms", serverDelayMs).build(id))
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
        .timeout(TIMEOUT)
        .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
            .filter(LectureApiClient::worthRetrying))
        .doOnNext(body -> log.info("[강의 조회] id={} 받아옴 - {}", id, body))
        .onErrorResume(e -> {
          log.warn("[강의 조회] id={} 실패 — {} : {}", id, e.getClass().getSimpleName(), e.getMessage());
          Map<String, Object> fallback = new LinkedHashMap<>();
          fallback.put("id", id);
          fallback.put("title", "(불러오지 못함)");
          return Mono.just(fallback);
        });
  }

  public Mono<List<Map<String, Object>>> fetchAll(List<Long> ids, long serverDelayMs) {
    return Flux.fromIterable(ids)
        .flatMap(id -> fetchLecture(id, serverDelayMs))
        .collectList();
  }

  private static boolean worthRetrying(Throwable e) {
    if (e instanceof WebClientResponseException http) {
      return http.getStatusCode().is5xxServerError();
    }
    return e instanceof TimeoutException;
  }

}
