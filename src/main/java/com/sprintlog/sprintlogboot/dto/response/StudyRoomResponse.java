package com.sprintlog.sprintlogboot.dto.response;

import com.sprintlog.sprintlogboot.domain.StudyRoom;

public record StudyRoomResponse(
    Long id, String name, String topic, int memberCount
) {

  public static StudyRoomResponse of(StudyRoom room, int memberCount) {
    return new StudyRoomResponse(room.getId(), room.getName(), room.getTopic(), memberCount);
  }
}
