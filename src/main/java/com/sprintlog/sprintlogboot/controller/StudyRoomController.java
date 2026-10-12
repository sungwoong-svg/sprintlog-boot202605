package com.sprintlog.sprintlogboot.controller;

import com.sprintlog.sprintlogboot.dto.response.StudyRoomResponse;
import com.sprintlog.sprintlogboot.repository.StudyRoomRepository;
import com.sprintlog.sprintlogboot.websocket.StudyRoomPresence;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/study-rooms")
@RequiredArgsConstructor
public class StudyRoomController {

  private final StudyRoomRepository repository;
  private final StudyRoomPresence presence; // 접속자 파악 장부

  @GetMapping
  public List<StudyRoomResponse> list() {
    return repository.findAll().stream()
        .map(room -> StudyRoomResponse.of(room, presence.count(room.getId())))
        .toList();
  }

}
