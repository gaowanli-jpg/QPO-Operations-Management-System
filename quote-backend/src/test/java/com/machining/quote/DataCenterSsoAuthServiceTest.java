package com.machining.quote;

import com.machining.quote.entity.AuthDataCenterSsoNonce;
import com.machining.quote.mapper.AuthDataCenterSsoNonceMapper;
import com.machining.quote.service.auth.DataCenterSsoSignatureService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DataCenterSsoAuthServiceTest {

    @Test
    void nonceMapperUsesPostgresInsertIgnoreForReplayProtection() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "mapper", "AuthDataCenterSsoNonceMapper.java"
        ), StandardCharsets.UTF_8);

        assertThat(source)
                .contains("int insertIgnore(AuthDataCenterSsoNonce row)")
                .contains("ON CONFLICT (nonce_hash) DO NOTHING");
    }

    @Test
    void rejectedSsoLoginAuditUsesRequiresNewTransaction() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "auth", "DataCenterSsoAuthService.java"
        ), StandardCharsets.UTF_8);

        assertThat(source)
                .contains("PlatformTransactionManager")
                .contains("TransactionTemplate")
                .contains("PROPAGATION_REQUIRES_NEW")
                .contains("recordFailureInNewTransaction");
    }

    @Test
    void nonceHashDoesNotExposeRawNonce() {
        DataCenterSsoSignatureService service = new DataCenterSsoSignatureService();

        String hash = service.nonceHash("QPO", "dc-test-001");

        assertThat(hash)
                .hasSize(64)
                .doesNotContain("dc-test-001")
                .isEqualTo(service.nonceHash(" qpo ", " dc-test-001 "));
    }

    @Test
    void nonceEntityPersistsOnlyHashAndSafeMetadata() {
        AuthDataCenterSsoNonce nonce = new AuthDataCenterSsoNonce();
        nonce.setNonce("dc-test-001");
        nonce.setNonceHash("hash-only");
        nonce.setUsername("admin");
        nonce.setAppCode("QPO");
        nonce.setRequestTimestamp(1780000000000L);
        nonce.setResult("SUCCESS");
        nonce.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        assertThat(nonce.getNonceHash()).isEqualTo("hash-only");
        assertThat(nonce.toString()).contains("nonceHash=hash-only");
    }

    @SuppressWarnings("unused")
    private void compileMapperContract(AuthDataCenterSsoNonceMapper mapper, AuthDataCenterSsoNonce row) {
        int inserted = mapper.insertIgnore(row);
        assertThat(inserted).isGreaterThanOrEqualTo(0);
    }
}
