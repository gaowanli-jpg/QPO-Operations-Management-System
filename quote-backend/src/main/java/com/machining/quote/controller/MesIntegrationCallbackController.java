package com.machining.quote.controller;

import com.machining.quote.dto.mes.MesMaterialCallbackReq;
import com.machining.quote.dto.mes.MesMaterialCallbackResp;
import com.machining.quote.service.mes.MesMaterialCallbackException;
import com.machining.quote.service.mes.MesMaterialSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/integrations/mes")
public class MesIntegrationCallbackController {
    @Autowired
    private MesMaterialSyncService mesMaterialSyncService;

    @Value("${erp.mes.callback-token:}")
    private String callbackToken;

    @Value("${erp.mes.callback-token-enabled:true}")
    private boolean callbackTokenEnabled;

    @PostMapping("/material-callback")
    public ResponseEntity<MesMaterialCallbackResp> materialCallback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) MesMaterialCallbackReq req) {
        if (!hasValidCallbackToken(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(failure("MES 回调 token 无效", req));
        }
        try {
            return ResponseEntity.ok(mesMaterialSyncService.handleMaterialCallback(req));
        } catch (MesMaterialCallbackException error) {
            return ResponseEntity.status(error.getStatus()).body(failure(error.getMessage(), req));
        }
    }

    private boolean hasValidCallbackToken(String authorization) {
        if (!callbackTokenEnabled) {
            return true;
        }
        String expected = callbackToken == null ? "" : callbackToken.trim();
        String actual = bearerToken(authorization);
        if (expected.isEmpty() || actual.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private MesMaterialCallbackResp failure(String message, MesMaterialCallbackReq req) {
        MesMaterialCallbackResp resp = new MesMaterialCallbackResp();
        resp.setSuccess(false);
        resp.setMessage(message);
        if (req != null) {
            resp.setTicketId(req.getTicketId());
            resp.setMesRequestId(req.getMesRequestId());
        }
        return resp;
    }
}
