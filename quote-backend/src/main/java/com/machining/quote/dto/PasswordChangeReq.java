package com.machining.quote.dto;

import lombok.Data;

@Data
public class PasswordChangeReq {
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
}
