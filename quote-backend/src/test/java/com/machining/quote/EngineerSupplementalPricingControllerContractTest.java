package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EngineerSupplementalPricingControllerContractTest {

    @Test
    void supplementalPricingOrderPickerUsesPricingPermissionForCustomersAndOrders() throws Exception {
        String source = Files.readString(
                Path.of("src", "main", "java", "com", "machining", "quote", "controller", "EngineerController.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("@GetMapping(\"/pricing/customers\")")
                .contains("@RequirePermission(\"engineer:pricing:view\")")
                .contains("return orderManagementService.pageCustomerOptions(field, keyword, page, pageSize)")
                .contains("@GetMapping(\"/pricing/supplemental-drawing/orders\")")
                .contains("return engineerService.listSupplementalPricingOrders(customerName, keyword, status, page, pageSize)");
    }
}
