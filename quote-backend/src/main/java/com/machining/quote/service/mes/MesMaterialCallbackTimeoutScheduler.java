package com.machining.quote.service.mes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MES 物料回调超时扫描器。
 *
 * <p>MES 只在成功时回调，失败不回调，所以必须自己发现「等不到的回调」。
 * 首次执行延后一个扫描周期，避免应用刚启动、上一次请求还在正常等待时就被误判。</p>
 */
@Component
public class MesMaterialCallbackTimeoutScheduler {
    private static final Logger log = LoggerFactory.getLogger(MesMaterialCallbackTimeoutScheduler.class);

    @Autowired
    private MesMaterialCallbackTimeoutService timeoutService;

    @Value("${erp.mes.material-callback-timeout.enabled:true}")
    private boolean enabled = true;

    @Scheduled(
            fixedDelayString = "${erp.mes.material-callback-timeout.check-ms:600000}",
            initialDelayString = "${erp.mes.material-callback-timeout.check-ms:600000}")
    public void expireOverdueCallbacks() {
        if (!enabled) {
            return;
        }
        try {
            timeoutService.expireOverdueCallbacks();
        } catch (RuntimeException error) {
            log.warn("MES material callback timeout scan failed", error);
        }
    }
}
