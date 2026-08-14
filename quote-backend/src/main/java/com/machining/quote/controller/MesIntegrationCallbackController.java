package com.machining.quote.controller;

import com.machining.quote.dto.mes.MesMaterialCallbackReq;
import com.machining.quote.dto.mes.MesMaterialCallbackResp;
import com.machining.quote.service.SystemInboundCallbackConfigService;
import com.machining.quote.service.mes.MesMaterialCallbackException;
import com.machining.quote.service.mes.MesMaterialSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
        if (!inboundCallbackConfigService.hasValidOpenApiBearerToken(authorization)) {
            return ResponseEntity.ok(failure(HttpStatus.UNAUTHORIZED, "QPO 入站回调 token 无效", req));
        }
        try {
            return ResponseEntity.ok(mesMaterialSyncService.handleMaterialBatchCallback(req));
        } catch (MesMaterialCallbackException error) {
            return ResponseEntity.ok(failure(error.getStatus(), error.getMessage(), req));
        } catch (RuntimeException error) {
            log.error("MES material callback failed bizId={} itemCount={} errorType={}",
                    req == null ? null : req.getMesRequestId(),
                    req == null || req.getItems() == null ? null : req.getItems().size(),
                    error.getClass().getSimpleName());
            return ResponseEntity.ok(failure(HttpStatus.INTERNAL_SERVER_ERROR, "MES material callback handling failed", req));
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MesMaterialCallbackResp> invalidRequestBody(HttpMessageNotReadableException error) {
        return ResponseEntity.ok(failure(HttpStatus.BAD_REQUEST, "MES material callback request body is invalid", null));
    }

    private MesMaterialCallbackResp failure(HttpStatus status, String message, MesMaterialCallbackReq req) {
        MesMaterialCallbackResp resp = new MesMaterialCallbackResp();
        resp.setSuccess(false);
        resp.setCode(status.value());
        resp.setMsg(message);
        if (req != null) {
            resp.setMesRequestId(req.getMesRequestId());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bizId", req == null ? null : req.getMesRequestId());
        data.put("receivedCount", req == null || req.getItems() == null ? 0 : req.getItems().size());
        data.put("appliedCount", 0);
        data.put("duplicateCount", 0);
        data.put("errors", List.of(message));
        resp.setData(data);
        return resp;
    }
}
