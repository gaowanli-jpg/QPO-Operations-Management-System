package com.machining.apssystem.dto;

import com.machining.apssystem.entity.ApsUser;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApsUserDTO {
    private Long id;
    private String username;
    private String displayName;
    private String role;
    private Boolean enabled;
    private Boolean forcePasswordChange;
    private Integer failedLoginAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime createdAt;

    public static ApsUserDTO from(ApsUser user) {
        if (user == null) {
            return null;
        }
        ApsUserDTO dto = new ApsUserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setDisplayName(user.getDisplayName());
        dto.setRole(user.getRole());
        dto.setEnabled(user.getEnabled());
        dto.setForcePasswordChange(user.getForcePasswordChange());
        dto.setFailedLoginAttempts(user.getFailedLoginAttempts());
        dto.setLockedUntil(user.getLockedUntil());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setPasswordChangedAt(user.getPasswordChangedAt());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}
