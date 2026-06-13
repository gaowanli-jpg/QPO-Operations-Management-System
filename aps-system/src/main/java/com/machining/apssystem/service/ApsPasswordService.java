package com.machining.apssystem.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ApsPasswordService {
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

    public String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }
        return PASSWORD_ENCODER.encode(rawPassword);
    }

    public boolean matchesPassword(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        if (passwordHash.startsWith("$2")) {
            return PASSWORD_ENCODER.matches(rawPassword, passwordHash);
        }
        return legacySha256(rawPassword).equalsIgnoreCase(passwordHash);
    }

    private String legacySha256(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }
}
