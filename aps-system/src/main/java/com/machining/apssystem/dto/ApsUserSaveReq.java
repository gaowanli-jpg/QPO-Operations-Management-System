package com.machining.apssystem.dto;

import lombok.Data;

@Data
public class ApsUserSaveReq {
    private String username;
    private String password;
    private String displayName;
    private String role;
    private Boolean enabled;
}
