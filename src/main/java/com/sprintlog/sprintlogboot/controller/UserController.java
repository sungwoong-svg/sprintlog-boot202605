package com.sprintlog.sprintlogboot.controller;

import com.sprintlog.sprintlogboot.dto.request.RoleUpdateRequest;
import com.sprintlog.sprintlogboot.dto.request.SignUpRequest;
import com.sprintlog.sprintlogboot.dto.response.UserResponse;
import com.sprintlog.sprintlogboot.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PostMapping
  public ResponseEntity<UserResponse> signUp(@Valid @RequestBody SignUpRequest request) {
    UserResponse register = userService.register(request);
    // 생성된 자원의 위치를 Location 헤더로 알려준다(REST 관례).
    URI location = URI.create("/api/v1/users/" + register.id());
    return ResponseEntity.created(location).body(register);
  }

  @PutMapping("/role")
  @PreAuthorize( "hasRole('ADMIN')")
  public ResponseEntity<UserResponse> changeRole(@Valid @RequestBody RoleUpdateRequest request) {
    return ResponseEntity.ok().body(userService.changeRole(request.email(), request.role()));
  }

}
