package com.machining.apssystem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "aps.auth.enabled=false"
})
class ApsSystemApplicationTests {

    @Test
    void contextLoads() {
    }

}
