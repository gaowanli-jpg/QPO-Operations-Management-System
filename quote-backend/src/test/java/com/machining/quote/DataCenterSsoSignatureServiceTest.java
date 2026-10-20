package com.machining.quote;

import com.machining.quote.dto.auth.DataCenterSsoExchangeReq;
import com.machining.quote.service.auth.DataCenterSsoRuntimeConfig;
import com.machining.quote.service.auth.DataCenterSsoSignatureService;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataCenterSsoSignatureServiceTest {

    @Test
    void canonicalStringSortsFieldsAndUsesDefaultRedirect() {
        DataCenterSsoSignatureService service = new DataCenterSsoSignatureService();
        DataCenterSsoExchangeReq req = request(1780000000000L, null, null);

        assertThat(service.canonicalString(req, "/dashboard"))
                .isEqualTo(String.join("\n",
                        "appCode=QPO",
                        "nonce=dc-test-001",
                        "redirect=/dashboard",
                        "timestamp=1780000000000",
                        "username=admin"));
    }

    @Test
    void signProducesBase64UrlHmacSha256WithoutPadding() throws Exception {
        DataCenterSsoSignatureService service = new DataCenterSsoSignatureService();
        DataCenterSsoExchangeReq req = request(1780000000000L, "/dashboard", null);
        String canonical = service.canonicalString(req, "/dashboard");

        assertThat(service.sign(req, "shared-secret", "/dashboard"))
                .isEqualTo(expectedSignature("shared-secret", canonical))
                .doesNotContain("=");
    }

    @Test
    void validateAcceptsCorrectSignatureInsideTimestampWindow() {
        DataCenterSsoSignatureService service = new DataCenterSsoSignatureService();
        DataCenterSsoExchangeReq req = request(1780000000000L, "/dashboard", null);
        req.setSignature(service.sign(req, "shared-secret", "/dashboard"));

        service.validate(req, runtimeConfig(), Instant.ofEpochMilli(1780000005000L));
    }

    @Test
    void validateRejectsBadSignatureExpiredTimestampFutureTimestampAndExternalRedirect() {
        DataCenterSsoSignatureService service = new DataCenterSsoSignatureService();
        DataCenterSsoRuntimeConfig config = runtimeConfig();
        DataCenterSsoExchangeReq badSignature = request(1780000000000L, "/dashboard", "bad");

        assertThatThrownBy(() -> service.validate(badSignature, config, Instant.ofEpochMilli(1780000001000L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature");

        DataCenterSsoExchangeReq expired = signed(service, request(1780000000000L, "/dashboard", null));
        assertThatThrownBy(() -> service.validate(expired, config, Instant.ofEpochMilli(1780000200000L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamp");

        DataCenterSsoExchangeReq future = signed(service, request(1780000200000L, "/dashboard", null));
        assertThatThrownBy(() -> service.validate(future, config, Instant.ofEpochMilli(1780000000000L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamp");

        DataCenterSsoExchangeReq external = signed(service, request(1780000000000L, "https://evil.example", null));
        assertThatThrownBy(() -> service.validate(external, config, Instant.ofEpochMilli(1780000001000L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("redirect");
    }

    private DataCenterSsoExchangeReq signed(DataCenterSsoSignatureService service, DataCenterSsoExchangeReq req) {
        req.setSignature(service.sign(req, "shared-secret", "/dashboard"));
        return req;
    }

    private DataCenterSsoExchangeReq request(long timestamp, String redirect, String signature) {
        DataCenterSsoExchangeReq req = new DataCenterSsoExchangeReq();
        req.setAppCode("QPO");
        req.setUsername("admin");
        req.setDisplayName("Admin");
        req.setTimestamp(timestamp);
        req.setNonce("dc-test-001");
        req.setRedirect(redirect);
        req.setSignature(signature);
        return req;
    }

    private DataCenterSsoRuntimeConfig runtimeConfig() {
        return new DataCenterSsoRuntimeConfig(
                "DB",
                true,
                "QPO",
                "shared-secret",
                120,
                10,
                "/dashboard",
                List.of("/", "/dashboard", "/orders")
        );
    }

    private String expectedSignature(String secret, String canonical) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
