package com.sprintlog.sprintlogboot.security;

import com.sprintlog.sprintlogboot.domain.Role;
import org.springframework.security.core.AuthenticatedPrincipal;

public record JwtPrincipal(Long id, String email, Role role) implements AuthenticatedPrincipal {

  @Override
  public String getName() {
    return email;
  }
}
