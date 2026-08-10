package com.machining.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.config.DataScopeAccessDeniedException;
import com.machining.quote.config.GlobalExceptionHandler;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.ReleaseItemDataReplacementController;
import com.machining.quote.dto.ReleaseItemDataReplacementPreviewDTO;
import com.machining.quote.service.ReleaseItemDataReplacementService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseItemDataReplacementStage3ControllerTest {
    @Test
    void previewEndpointUsesDedicatedPathMultipartAndReleaseCreatePermission() throws Exception {
        Method method = ReleaseItemDataReplacementController.class.getDeclaredMethod(
                "preview", String.class, org.springframework.web.multipart.MultipartFile.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        RequirePermission permission = method.getAnnotation(RequirePermission.class);

        assertThat(mapping.value()).containsExactly("/preview");
        assertThat(mapping.consumes()).containsExactly(MediaType.MULTIPART_FORM_DATA_VALUE);
        assertThat(permission.value()).containsExactly("market:release:create");
    }

    @Test
    void controllerParsesRfqSourcesAndDelegatesWithoutPersistingTheFile() throws Exception {
        ReleaseItemDataReplacementService service = mock(ReleaseItemDataReplacementService.class);
        ReleaseItemDataReplacementPreviewDTO expected = new ReleaseItemDataReplacementPreviewDTO();
        expected.setSuccess(true);
        when(service.preview(any(), any())).thenReturn(expected);
        ReleaseItemDataReplacementController controller = new ReleaseItemDataReplacementController(
                service, new ObjectMapper());
        MockMultipartFile file = new MockMultipartFile(
                "excelFile", "replacement.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});

        ReleaseItemDataReplacementPreviewDTO result = controller.preview(
                "[{\"sourceType\":\"RFQ\",\"sourceOrderId\":101}]", file);

        assertThat(result).isSameAs(expected);
        verify(service).preview(
                org.mockito.ArgumentMatchers.argThat(sources -> sources.size() == 1
                        && sources.get(0).getSourceOrderId().equals(101L)),
                any());
    }

    @Test
    void controllerRejectsInvalidJsonEmptyFilesAndNonExcelExtensionsBeforeService() {
        ReleaseItemDataReplacementService service = mock(ReleaseItemDataReplacementService.class);
        ReleaseItemDataReplacementController controller = new ReleaseItemDataReplacementController(
                service, new ObjectMapper());

        assertThatThrownBy(() -> controller.preview(
                "not-json",
                new MockMultipartFile("excelFile", "replacement.xlsx", "application/octet-stream", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("来源参数格式");
        assertThatThrownBy(() -> controller.preview(
                "[]",
                new MockMultipartFile("excelFile", "replacement.xlsx", "application/octet-stream", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> controller.preview(
                "[]",
                new MockMultipartFile("excelFile", "replacement.csv", "text/csv", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".xlsx")
                .hasMessageContaining(".xls");

        verify(service, never()).preview(any(), any());
    }

    @Test
    void dataScopeAccessDeniedMapsToForbiddenWithoutLeakingOrderDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleDataScopeAccessDenied(
                new DataScopeAccessDeniedException("当前账号无权访问所选询价单"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody()).containsEntry("message", "当前账号无权访问所选询价单");
        assertThat(response.getBody().toString()).doesNotContain("101", "RFQ", "客户");
    }
}
