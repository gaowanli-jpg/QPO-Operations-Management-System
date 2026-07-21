package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MesMaterialClientTest {
    private static final Path MES_CLIENT = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "MesMaterialClient.java");

    @Test
    void mesClientConfigurationAndHeaderRulesAreFrozen() throws IOException {
        String source = read();

        assertThat(source)
                .contains(
                        "class MesMaterialClient",
                        "@Value(\"${erp.mes.base-url:}\")",
                        "@Value(\"${erp.mes.auth-token:}\")",
                        "@Value(\"${erp.mes.material-path:/api/mes/materials/resolve}\")",
                        "@Value(\"${erp.mes.timeout-ms:10000}\")",
                        "resolveMaterial(MesMaterialResolveReq req)",
                        "Authorization",
                        "Bearer"
                );
    }

    @Test
    void mesClientFailureContractAvoidsTokenAndPayloadLogging() throws IOException {
        String source = read();

        assertThat(source)
                .contains(
                        "MES base URL is not configured",
                        "HttpStatusCodeException",
                        "RestTemplate",
                        "setConnectTimeout",
                        "setReadTimeout"
                )
                .doesNotContain(
                        "mesAuthToken={}",
                        "authToken={}",
                        "requestBody={}",
                        "fullJson"
                );
    }

    private String read() throws IOException {
        assertThat(Files.exists(MES_CLIENT))
                .as("%s should exist", MES_CLIENT)
                .isTrue();
        return Files.readString(MES_CLIENT, StandardCharsets.UTF_8);
    }
}
