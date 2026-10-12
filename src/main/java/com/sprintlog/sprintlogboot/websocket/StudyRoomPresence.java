package com.sprintlog.sprintlogboot.websocket;

import com.sprintlog.sprintlogboot.websocket.StudyRoomMessages.Member;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class StudyRoomPresence {

  private final Map<Long, Map<String, Member>> rooms = new ConcurrentHashMap<>();

  /** 들어오기 — 같은 세션이 또 들어와도 한 줄(재연결 때 join 을 다시 보내도 안전하다). */
  public void join(Long roomId, String sessionId, String nickname) {
    rooms.computeIfAbsent(roomId, id -> new ConcurrentHashMap<>())
        .compute(sessionId, (sid, before) -> new Member(nickname, before != null && before.studying()));
  }

  public void updateStatus(Long roomId, String sessionId, boolean studying) {
    Map<String, Member> members = rooms.get(roomId);
    if (members == null) {
      return;
    }
    members.computeIfPresent(sessionId, (sid, m) -> new Member(m.nickname(), studying));
  }

  /** 나가기 — 나간 사람의 이름을 돌려준다(없었으면 null). */
  public String leave(Long roomId, String sessionId) {
    Map<String, Member> members = rooms.get(roomId);
    if (members == null) {
      return null;
    }
    Member removed = members.remove(sessionId);
    return removed == null ? null : removed.nickname();
  }

  public List<Member> members(Long roomId) {
    return rooms.getOrDefault(roomId, Map.of()).values().stream()
        .sorted(Comparator.comparing(Member::nickname))
        .toList();
  }

  public int count(Long roomId) {
    return rooms.getOrDefault(roomId, Map.of()).size();
  }
}
