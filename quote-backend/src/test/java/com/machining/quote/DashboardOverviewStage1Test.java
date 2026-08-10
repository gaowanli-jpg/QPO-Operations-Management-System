package com.machining.quote;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.DashboardController;
import com.machining.quote.dto.dashboard.DashboardOverviewDTO;
import com.machining.quote.service.DashboardOverviewService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardOverviewStage1Test {
    @Test
    void emptyOverviewKeepsStableDashboardContract() {
        DashboardOverviewDTO overview = new DashboardOverviewService().overview();

        assertThat(overview.getUpdatedAt()).isNotBlank();
        assertThat(overview.getSummaryCards()).isEmpty();
        assertThat(overview.getRfqStatusDistribution()).isEmpty();
        assertThat(overview.getU9ReadyCustomerDistribution()).isEmpty();
        assertThat(overview.getCustomerMatrix()).isEmpty();
        assertThat(overview.getTodoCards()).isEmpty();
        assertThat(overview.getAttentionItems()).isEmpty();
    }

    @Test
    void controllerExposesReadOnlyOverviewEndpointWithoutBusinessMenuPermission() throws Exception {
        DashboardController controller = new DashboardController(new DashboardOverviewService());
        Method method = DashboardController.class.getMethod("overview");

        assertThat(DashboardController.class.getAnnotation(RestController.class)).isNotNull();
        assertThat(DashboardController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/api/dashboard");
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/overview");
        assertThat(method.getAnnotation(RequirePermission.class)).isNull();

        DashboardOverviewDTO overview = controller.overview();
        assertThat(overview.getUpdatedAt()).isNotBlank();
        assertThat(overview.getSummaryCards()).isEmpty();
        assertThat(overview.getRfqStatusDistribution()).isEmpty();
        assertThat(overview.getU9ReadyCustomerDistribution()).isEmpty();
        assertThat(overview.getCustomerMatrix()).isEmpty();
        assertThat(overview.getTodoCards()).isEmpty();
        assertThat(overview.getAttentionItems()).isEmpty();
    }
}
