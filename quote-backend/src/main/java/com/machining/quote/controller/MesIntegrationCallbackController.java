package com.machining.quote.controller;

import com.machining.quote.dto.mes.MesMaterialCallbackReq;
import com.machining.quote.dto.mes.MesMaterialCallbackResp;
import com.machining.quote.integration.InboundCallbackCode;
import com.machining.quote.service.SystemInboundCallbackConfigService;
import com.machining.quote.service.mes.MesMaterialCallbackException;
import com.machining.quote.service.mes.MesMaterialSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/integrations/mes")
public class MesIntegrationCallbackController {
    private static final Logger log = LoggerFactory.getLogger(MesIntegrationCallbackController.class);

    @Autowired
    private MesMaterialSyncService mesMaterialSyncService;
    @Autowired
    private SystemInboundCallbackConfigService inboundCallbackConfigService;

    @PostMapping("/material-callback")
    public ResponseEntity<MesMaterialCallbackResp> materialCallback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) MesMaterialCallbackReq req) {
        if (!inboundCallbackConfigService.hasValidBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(failure(HttpStatus.UNAUTHORIZED, "QPO 入站回调 token 无效", req));
        }
        try {
            if (isBatchCallback(req)) {
                return ResponseEntity.ok(mesMaterialSyncService.handleMaterialBatchCallback(req));
            }
            return ResponseEntity.ok(mesMaterialSyncService.handleMaterialCallback(req));
        } catch (MesMaterialCallbackException error) {
            return ResponseEntity.status(error.getStatus()).body(failure(error.getStatus(), error.getMessage(), req));
        } catch (RuntimeException error) {
            log.error("MES material callback failed bizId={} itemCount={} errorType={}",
                    req == null ? null : req.getMesRequestId(),
                    req == null || req.getItems() == null ? null : req.getItems().size(),
                    error.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(failure(HttpStatus.INTERNAL_SERVER_ERROR, "MES material callback handling failed", req));
        }
    }

    private boolean isBatchCallback(MesMaterialCallbackReq req) {
        return req != null && req.getItems() != null;
    }

    private MesMaterialCallbackResp failure(HttpStatus status, String message, MesMaterialCallbackReq req) {
        MesMaterialCallbackResp resp = new MesMaterialCallbackResp();
        resp.setSuccess(false);
        resp.setCode(status.value());
        resp.setMsg(message);
        resp.setMessage(message);
        if (req != null) {
            resp.setMesRequestId(req.getMesRequestId());
        }
        if (isBatchCallback(req)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("bizId", req.getMesRequestId());
            data.put("receivedCount", req.getItems() == null ? 0 : req.getItems().size());
            data.put("appliedCount", 0);
            data.put("duplicateCount", 0);
            data.put("errors", List.of(message));
            resp.setData(data);
        }
        return resp;
    }
}
