package com.machining.quote;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "erp.auth.jwt-secret=test-jwt-secret-for-context-loads-32chars",
        "erp.upload-dir=${java.io.tmpdir}/quote-test/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-test/backups/"
})
class QuoteSystemApplicationTests {

    @Test
    void contextLoads() {
    }

}
