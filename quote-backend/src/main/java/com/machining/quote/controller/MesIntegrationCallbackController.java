package com.machining.quote.controller;

import com.machining.quote.dto.mes.MesMaterialCallbackReq;
import com.machining.quote.dto.mes.MesMaterialCallbackResp;
import com.machining.quote.integration.InboundCallbackCode;
import com.machining.quote.service.SystemInboundCallbackConfigService;
import com.machining.quote.service.mes.MesMaterialCallbackException;
import com.machining.quote.service.mes.MesMaterialSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/mes")
public class MesIntegrationCallbackController {
    @Autowired
    private MesMaterialSyncService mesMaterialSyncService;
    @Autowired
    private SystemInboundCallbackConfigService inboundCallbackConfigService;

    @PostMapping("/material-callback")
    public ResponseEntity<MesMaterialCallbackResp> materialCallback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) MesMaterialCallbackReq req) {
        if (!inboundCallbackConfigService.hasValidBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(failure("QPO 入站回调 token 无效", req));
        }
        try {
            return ResponseEntity.ok(mesMaterialSyncService.handleMaterialCallback(req));
        } catch (MesMaterialCallbackException error) {
            return ResponseEntity.status(error.getStatus()).body(failure(error.getMessage(), req));
        }
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
