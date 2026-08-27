package com.machining.quote;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.EngineerController;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.PricingMaterialOptionDTO;
import com.machining.quote.dto.PricingOptionPageReq;
import com.machining.quote.dto.PricingProcessOptionDTO;
import com.machining.quote.service.EngineeringPricingOptionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineerPricingOptionControllerTest {

    @Test
    void controllerExposesPricingMaterialOptionsWithPricingViewPermission() throws Exception {
        Method method = EngineerController.class.getDeclaredMethod("pagePricingMaterialOptions", PricingOptionPageReq.class);

        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/pricing/options/materials");
        assertThat(method.getAnnotation(RequirePermission.class).value()).containsExactly("engineer:pricing:view");
    }

    @Test
    void controllerExposesPricingProcessOptionsWithPricingViewPermission() throws Exception {
        Method method = EngineerController.class.getDeclaredMethod("pagePricingProcessOptions", PricingOptionPageReq.class);

        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/pricing/options/processes");
        assertThat(method.getAnnotation(RequirePermission.class).value()).containsExactly("engineer:pricing:view");
    }

    @Test
    void controllerDelegatesMaterialAndProcessOptionQueriesToReadOnlyService() {
        EngineerController controller = new EngineerController();
        EngineeringPricingOptionService service = mock(EngineeringPricingOptionService.class);
        ReflectionTestUtils.setField(controller, "pricingOptionService", service);
        PricingOptionPageReq req = new PricingOptionPageReq();
        PageResult<PricingMaterialOptionDTO> materialPage = PageResult.of(List.of(), 0, 1, 20);
        PageResult<PricingProcessOptionDTO> processPage = PageResult.of(List.of(), 0, 1, 20);
        when(service.pageMaterialOptions(req)).thenReturn(materialPage);
        when(service.pageProcessOptions(req)).thenReturn(processPage);

        assertThat(controller.pagePricingMaterialOptions(req)).isSameAs(materialPage);
        assertThat(controller.pagePricingProcessOptions(req)).isSameAs(processPage);
        verify(service).pageMaterialOptions(req);
        verify(service).pageProcessOptions(req);
    }

    @Test
    void stage6DocsStateRemotePricingOptionsReusePricingViewPermissionAndSkipOperationLogs() throws Exception {
        String permissionDoc = Files.readString(Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md"), StandardCharsets.UTF_8);
        String logDoc = Files.readString(Path.of("..", "docs", "QPO日志模块", "03-日志运维说明.md"), StandardCharsets.UTF_8);

        assertThat(permissionDoc)
                .contains("材料 / 工艺远程分页选项查询复用 `engineer:pricing:view`")
                .contains("不新增权限点")
                .contains("不新增 `common:*`");
        assertThat(logDoc)
                .contains("材料 / 工艺远程分页选项查询")
                .contains("不写操作日志")
                .contains("不新增高频业务 INFO");
    }
}
