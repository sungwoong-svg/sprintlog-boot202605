package com.sprintlog.sprintlogboot.websocket;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class StudyRoomMessages {

  private static final DateTimeFormatter SENT_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

  private StudyRoomMessages() {
  }

  /** 브라우저 → 서버: 채팅 한 줄. */
  public record ChatRequest(String content) {
  }

  /** 브라우저 → 서버: 공부 시작/정지. */
  public record StatusRequest(boolean studying) {
  }

  /** 서버 → 방 전체: 무슨 일이 있었나. type = CHAT · JOIN · LEAVE · STATUS */
  public record RoomMessage(String type, Long roomId, String sender, String content, Boolean studying, String sentAt) {

    public static RoomMessage of(String type, Long roomId, String sender, String content, Boolean studying) {
      return new RoomMessage(type, roomId, sender, content, studying, LocalDateTime.now().format(SENT_AT));
    }
  }

  /** 서버 → 방 전체: 지금 방에 있는 사람들. */
  public record Presence(Long roomId, List<Member> members) {
  }

  /** 접속자 한 명 — 이름과 공부 중인지. */
  public record Member(String nickname, boolean studying) {
  }
}
