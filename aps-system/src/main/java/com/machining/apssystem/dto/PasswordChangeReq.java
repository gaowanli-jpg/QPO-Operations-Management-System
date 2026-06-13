package com.machining.apssystem.dto;

import lombok.Data;

@Data
public class PasswordChangeReq {
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
}
