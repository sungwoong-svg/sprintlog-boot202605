package com.sprintlog.sprintlogboot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * WebSocket 을 '브라우저 없이' 바이트 단위로 들여다본다.
 *
 * 브라우저 개발자 도구가 보여 주는 핸드셰이크와 프레임을, 소켓으로 직접 주고받으며 확인한다.
 * 상대는 개발용 메아리(/ws/echo) — 받은 글자를 "받았다: …" 로 돌려준다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "sprintlog.ws.echo-enabled=true")
@ActiveProfiles("test")
@DisplayName("WebSocket 프로토콜을 바이트로 본다")
class WebSocketProtocolTest {

  /** RFC 6455 문서 1.3절에 실린 예시 키. 서버가 표준대로 계산하면 아래 값이 나와야 한다. */
  private static final String RFC_SAMPLE_KEY = "dGhlIHNhbXBsZSBub25jZQ==";
  private static final String RFC_SAMPLE_ACCEPT = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";

  @LocalServerPort int port;

  @Test
  @DisplayName("핸드셰이크 — HTTP 로 '바꾸자' 고 하면 101 로 답한다")
  void 핸드셰이크() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      List<String> response = handshake(socket, true);

      System.out.println("[관찰] ── 우리가 보낸 핸드셰이크 요청 ──");
      System.out.print(upgradeRequest(true));
      System.out.println("[관찰] ── 서버의 응답 ──");
      response.forEach(line -> System.out.println("[관찰] " + line));

      assertThat(response.get(0)).startsWith("HTTP/1.1 101");
      assertThat(header(response, "Upgrade")).isEqualToIgnoringCase("websocket");
    }
  }

  @Test
  @DisplayName("Sec-WebSocket-Accept = 키 + 약속된 문자열을 SHA-1 → Base64 (표준 문서의 예시와 같다)")
  void 수락_값은_계산된다() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      List<String> response = handshake(socket, true);
      String accept = header(response, "Sec-WebSocket-Accept");

      // 우리가 직접 계산해 본다 — 서버가 '키를 정말 읽었다' 는 증거다.
      String guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
      byte[] sha1 = MessageDigest.getInstance("SHA-1")
          .digest((RFC_SAMPLE_KEY + guid).getBytes(StandardCharsets.US_ASCII));
      String computed = Base64.getEncoder().encodeToString(sha1);

      System.out.println("[관찰] 보낸 키         = " + RFC_SAMPLE_KEY);
      System.out.println("[관찰] 서버가 준 수락값 = " + accept);
      System.out.println("[관찰] 우리가 계산한 값 = " + computed);

      assertThat(accept).isEqualTo(computed).isEqualTo(RFC_SAMPLE_ACCEPT);
    }
  }

  @Test
  @DisplayName("Upgrade 헤더가 없으면 101 이 아니다 — 평범한 HTTP 요청으로 끝난다")
  void 업그레이드_없으면_101_아님() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      List<String> response = handshake(socket, false);
      System.out.println("[관찰] Upgrade 없이 보낸 요청의 응답 = " + response.get(0));
      assertThat(response.get(0)).doesNotStartWith("HTTP/1.1 101");
    }
  }

  @Test
  @DisplayName("프레임 — 보내는 쪽(브라우저)은 가리고(mask), 서버는 그대로 보낸다")
  void 글자_프레임() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      handshake(socket, true);
      byte[] payload = "안녕".getBytes(StandardCharsets.UTF_8);
      byte[] mask = {0x11, 0x22, 0x33, 0x44};

      byte[] sent = maskedFrame(0x1, payload, mask);
      socket.getOutputStream().write(sent);
      socket.getOutputStream().flush();

      byte[] received = readFrame(socket.getInputStream());
      int opcode = received[0] & 0x0F;
      boolean fin = (received[0] & 0x80) != 0;
      boolean masked = (received[1] & 0x80) != 0;
      int length = received[1] & 0x7F;
      String text = new String(received, 2, length, StandardCharsets.UTF_8);

      System.out.println("[관찰] 보낸 프레임(가림)   = " + hex(sent));
      System.out.println("[관찰] 받은 프레임(그대로) = " + hex(received));
      System.out.println("[관찰] 받은 프레임 풀이 — FIN=" + fin + " · opcode=" + opcode
          + "(1=글자) · 가림=" + masked + " · 길이=" + length + " · 내용=\"" + text + "\"");

      assertThat(fin).isTrue();
      assertThat(opcode).isEqualTo(0x1);
      assertThat(masked).isFalse();               // 서버 → 브라우저 프레임은 가리지 않는다
      assertThat(text).isEqualTo("받았다: 안녕");
    }
  }

  @Test
  @DisplayName("닫기 — 닫기 프레임(opcode 8)을 보내면 서버도 닫기 프레임으로 답한다")
  void 닫기_프레임() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      handshake(socket, true);
      byte[] normalClosure = {0x03, (byte) 0xE8};   // 1000 = 정상 종료
      socket.getOutputStream().write(maskedFrame(0x8, normalClosure, new byte[]{1, 2, 3, 4}));
      socket.getOutputStream().flush();

      byte[] received = readFrame(socket.getInputStream());
      int opcode = received[0] & 0x0F;
      int code = ((received[2] & 0xFF) << 8) | (received[3] & 0xFF);
      System.out.println("[관찰] 서버의 닫기 프레임 = " + hex(received) + " · opcode=" + opcode + " · 코드=" + code);

      assertThat(opcode).isEqualTo(0x8);
      assertThat(code).isEqualTo(1000);
    }
  }

  // ── 도우미 — 브라우저가 대신 해 주던 일을 손으로 한다 ────────────────────────

  private String upgradeRequest(boolean withUpgrade) {
    StringBuilder sb = new StringBuilder();
    sb.append("GET /ws/echo HTTP/1.1\r\n");
    sb.append("Host: localhost:").append(port).append("\r\n");
    if (withUpgrade) {
      sb.append("Upgrade: websocket\r\n");
      sb.append("Connection: Upgrade\r\n");
    }
    sb.append("Sec-WebSocket-Key: ").append(RFC_SAMPLE_KEY).append("\r\n");
    sb.append("Sec-WebSocket-Version: 13\r\n");
    if (!withUpgrade) {
      sb.append("Connection: close\r\n");
    }
    sb.append("\r\n");
    return sb.toString();
  }

  /** 요청을 보내고, 응답의 상태 줄과 헤더(빈 줄 전까지)를 읽는다. */
  private List<String> handshake(Socket socket, boolean withUpgrade) throws IOException {
    OutputStream out = socket.getOutputStream();
    out.write(upgradeRequest(withUpgrade).getBytes(StandardCharsets.US_ASCII));
    out.flush();

    InputStream in = socket.getInputStream();
    List<String> lines = new ArrayList<>();
    ByteArrayOutputStream line = new ByteArrayOutputStream();
    int prev = -1;
    int b;
    while ((b = in.read()) != -1) {
      if (prev == '\r' && b == '\n') {
        byte[] bytes = line.toByteArray();
        String text = new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
        if (text.isEmpty()) {
          break;                      // 빈 줄 = 헤더 끝. 이 뒤부터는 HTTP 가 아니다
        }
        lines.add(text);
        line.reset();
      } else {
        line.write(b);
      }
      prev = b;
    }
    return lines;
  }

  private static String header(List<String> response, String name) {
    return response.stream()
        .filter(l -> l.toLowerCase().startsWith(name.toLowerCase() + ":"))
        .map(l -> l.substring(l.indexOf(':') + 1).trim())
        .findFirst()
        .orElse(null);
  }

  /** 브라우저 → 서버 프레임: 항상 4바이트 마스크로 내용을 가린다(짧은 길이 전용). */
  private static byte[] maskedFrame(int opcode, byte[] payload, byte[] mask) {
    byte[] frame = new byte[2 + 4 + payload.length];
    frame[0] = (byte) (0x80 | opcode);            // FIN=1 + opcode
    frame[1] = (byte) (0x80 | payload.length);    // MASK=1 + 길이(125 이하)
    System.arraycopy(mask, 0, frame, 2, 4);
    for (int i = 0; i < payload.length; i++) {
      frame[6 + i] = (byte) (payload[i] ^ mask[i % 4]);
    }
    return frame;
  }

  /** 서버 → 브라우저 프레임 하나를 읽는다(가림 없음, 짧은 길이 전용). */
  private static byte[] readFrame(InputStream in) throws IOException {
    int first = in.read();
    int second = in.read();
    int length = second & 0x7F;
    byte[] frame = new byte[2 + length];
    frame[0] = (byte) first;
    frame[1] = (byte) second;
    int read = 0;
    while (read < length) {
      int n = in.read(frame, 2 + read, length - read);
      if (n == -1) break;
      read += n;
    }
    return frame;
  }

  private static String hex(byte[] bytes) {
    return HexFormat.ofDelimiter(" ").formatHex(bytes);
  }
}
