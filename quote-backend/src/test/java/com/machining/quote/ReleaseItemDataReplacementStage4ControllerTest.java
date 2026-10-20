package com.machining.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.config.GlobalExceptionHandler;
import com.machining.quote.config.PreviewFingerprintConflictException;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.ReleaseItemDataReplacementController;
import com.machining.quote.dto.ReleaseItemDataReplacementConfirmDTO;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.ReleaseItemDataReplacementService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseItemDataReplacementStage4ControllerTest {
    @Test
    void confirmEndpointUsesMultipartAndReleaseCreatePermission() throws Exception {
        Method method = ReleaseItemDataReplacementController.class.getDeclaredMethod(
                "confirm", String.class, String.class, org.springframework.web.multipart.MultipartFile.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        RequirePermission permission = method.getAnnotation(RequirePermission.class);

        assertThat(mapping.value()).containsExactly("/confirm");
        assertThat(mapping.consumes()).containsExactly(MediaType.MULTIPART_FORM_DATA_VALUE);
        assertThat(permission.value()).containsExactly("market:release:create");
    }

    @Test
    void controllerDelegatesConfirmAndRejectsMissingFingerprintBeforeService() throws Exception {
        ReleaseItemDataReplacementService service = mock(ReleaseItemDataReplacementService.class);
        ReleaseItemDataReplacementConfirmDTO expected = new ReleaseItemDataReplacementConfirmDTO();
        expected.setSuccess(true);
        when(service.confirm(any(), any(), eq("a".repeat(64)))).thenReturn(expected);
        ReleaseItemDataReplacementController controller = new ReleaseItemDataReplacementController(
                service, new ObjectMapper());
        MockMultipartFile file = new MockMultipartFile(
                "excelFile", "replacement.xlsx", "application/octet-stream", new byte[]{1, 2, 3});

        ReleaseItemDataReplacementConfirmDTO result = controller.confirm(
                "[{\"sourceType\":\"RFQ\",\"sourceOrderId\":101}]",
                "a".repeat(64),
                file);

        assertThat(result).isSameAs(expected);
        verify(service).confirm(
                org.mockito.ArgumentMatchers.argThat(sources -> sources.size() == 1
                        && sources.get(0).getSourceOrderId().equals(101L)),
                any(),
                eq("a".repeat(64)));

        assertThatThrownBy(() -> controller.confirm("[]", " ", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("预览指纹");
        verify(service, never()).confirm(any(), any(), eq(" "));
    }

    @Test
    void fingerprintConflictMapsToSafeConflictResponseAndAuditLabelIsRegistered() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handlePreviewFingerprintConflict(
                new PreviewFingerprintConflictException("internal item 201 changed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody()).containsEntry("message", "数据已发生变化，请重新预览");
        assertThat(response.getBody().toString()).doesNotContain("201", "internal");
        assertThat(AuditService.labelForAction("RELEASE_ITEM_DATA_REPLACEMENT"))
                .isEqualTo("议价提单表格数据替换");
    }
}
