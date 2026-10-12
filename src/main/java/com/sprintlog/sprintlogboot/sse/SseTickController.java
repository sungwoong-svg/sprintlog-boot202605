package com.sprintlog.sprintlogboot.sse;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Slf4j
@RestController
@ConditionalOnProperty(name = "sprintlog.sse.demo-enabled", havingValue = "true")
public class SseTickController {

  /** 연결 한 번에 보낼 개수. 다 보내면 응답을 끝낸다. */
  private static final int TICKS_PER_CONNECTION = 5;

  @GetMapping(value = "/sse/ticks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<StreamingResponseBody> ticks(
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
    // 다시 붙을 때 브라우저가 '마지막으로 받은 번호' 를 헤더로 알려 준다. 처음이면 없다.
    int last = lastEventId == null ? 0 : Integer.parseInt(lastEventId.trim());
    log.info("[SSE] 연결됨 — Last-Event-ID {} · 일꾼 {}",
        lastEventId == null ? "(없음)" : lastEventId, Thread.currentThread().getName());

    StreamingResponseBody body = out -> {
      Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
      send(w, "retry: 3000\n\n");                                           // 끊기면 3초 뒤 다시 붙어라
      send(w, ": 연결됨 — 콜론으로 시작하는 줄은 주석이다(화면엔 안 보인다)\n\n");
      for (int n = last + 1; n <= last + TICKS_PER_CONNECTION; n++) {
        send(w, "id: " + n + "\n"
            + "event: tick\n"
            + "data: {\"n\":" + n + ",\"at\":\"" + LocalTime.now().withNano(0) + "\"}\n\n");
        log.info("[SSE] 보냄 — {}번 · 일꾼 {}", n, Thread.currentThread().getName());
        pause();
      }
      send(w, "event: bye\n"
          + "data: 다섯 번 보냈으니 응답을 끝낸다\n"
          + "data: 브라우저는 3초 뒤 다시 붙는다\n\n");                     // data 두 줄 = 한 이벤트(줄바꿈으로 이어진다)
      log.info("[SSE] 응답 끝 — {}번까지 보냈다", last + TICKS_PER_CONNECTION);
    };
    return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(body);
  }

  /**
   * 쓰고 바로 내보낸다. flush 하지 않으면 글자가 Writer 의 버퍼에 남는다 —
   * 이 코드는 Writer 를 닫지 않으니 끝내 한 글자도 안 나간다(로그는 '보냄' 이라고 찍히는데도).
   */
  private static void send(Writer w, String chunk) throws IOException {
    w.write(chunk);
    w.flush();
  }

  private static void pause() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
