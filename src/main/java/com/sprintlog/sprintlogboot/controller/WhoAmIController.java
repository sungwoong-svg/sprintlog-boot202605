package com.sprintlog.sprintlogboot.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WhoAmIController {

  @GetMapping("/whoami")
  public Map<String, String> whoami() {
    return Map.of("host", System.getenv().getOrDefault("HOSTNAME", "unknown"));
  }

}