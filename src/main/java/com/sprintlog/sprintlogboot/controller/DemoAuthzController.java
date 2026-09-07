package com.sprintlog.sprintlogboot.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;

public class DemoAuthzController {

  @GetMapping("/api/v1/admin/dashboard")
  public Map<String, String> adminDashboard() {
    return Map.of("area", "admin", "message", "관리자 전용 대시보드입니다.");
  }

  @GetMapping("/api/v1/me/profile")
  public Map<String, String> myProfile() {
    return Map.of("area", "me", "message", "로그인한 사용자 전용 영역입니다.");
  }

}
