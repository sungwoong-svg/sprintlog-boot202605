package com.sprintlog.sprintlogboot.dto.request;

import com.sprintlog.sprintlogboot.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(
    @Email @NotNull String email,
    @NotNull Role role
) {

}
