package com.sprintlog.sprintlogboot.config;

import com.sprintlog.sprintlogboot.filter.RequestLoggingFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class TraceIdForwardingFilter implements ExchangeFilterFunction {

  /** 번호를 실어 보낼 헤더 이름. 받는 쪽(RequestLoggingFilter)도 이 이름으로 읽는다. */
  public static final String TRACE_HEADER = RequestLoggingFilter.TRACE_HEADER;

  @Override
  public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
    String traceId = MDC.get(RequestLoggingFilter.TRACE_ID);

    if (traceId == null) {
      log.info("[WebClient] {} {} — 추적 번호 없음", request.method(), request.url());
      return next.exchange(request);
    }

    log.info("[WebClient] {} {} — 추적 번호 {} 를 실어 보낸다", request.method(), request.url(), traceId);
    return next.exchange(
        ClientRequest.from(request)
            .header(TRACE_HEADER, traceId)
            .build());
  }
}
