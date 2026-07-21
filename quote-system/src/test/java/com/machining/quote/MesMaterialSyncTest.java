package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MesMaterialSyncTest {
    private static final Path ORDER_DELIVERY_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "OrderDeliveryController.java");
    private static final Path SYSTEM_TASK_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "SystemTaskService.java");
    private static final Path MES_SYNC_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "MesMaterialSyncService.java");
    private static final Path MES_REQ = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "MesMaterialResolveReq.java");
    private static final Path MES_RESP = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "MesMaterialResolveResp.java");

    @Test
    void deliveredItemMesSyncEndpointIsPermissionProtected() throws IOException {
        String controller = read(ORDER_DELIVERY_CONTROLLER);

        assertThat(controller)
                .as("MES material sync must be triggered from delivered-item module only")
                .contains(
                        "SystemTaskService",
                        "@PostMapping(\"/tickets/{ticketId}/mes-material\")",
                        "@RequirePermission(\"market:delivered-item:mes-sync\")",
                        "submitMesMaterialSyncTask(ticketId)"
                );
    }

    @Test
    void mesMaterialSyncTaskFlowFreezesStatusAndAuditNames() throws IOException {
        String taskService = read(SYSTEM_TASK_SERVICE);
        String syncService = read(MES_SYNC_SERVICE);

        assertThat(taskService)
                .as("System tasks need a distinct MES material sync task type")
                .contains(
                        "MES_MATERIAL_SYNC",
                        "submitMesMaterialSyncTask",
                        "DELIVERY_MES_MATERIAL_SYNC_SUBMIT",
                        "dataScopeService.assertCanAccessOrder"
                );
        assertThat(syncService)
                .as("Ticket status transitions and audit actions must be stable before implementation continues")
                .contains(
                        "PROCESSING",
                        "SUCCESS",
                        "FAILED",
                        "setMesSyncStatus",
                        "DELIVERY_MES_MATERIAL_SYNC_SUCCESS",
                        "DELIVERY_MES_MATERIAL_SYNC_FAILED",
                        "MES material sync started",
                        "MES material sync completed",
                        "MES material sync failed"
                );
    }

    @Test
    void mesMaterialDtosCarryTheExternalContractFields() throws IOException {
        assertThat(read(MES_REQ))
                .contains(
                        "class MesMaterialResolveReq",
                        "private Integer ticketId;",
                        "private Integer rfqId;",
                        "private String rfqNo;",
                        "private String customerName;",
                        "private String partNo;",
                        "private String version;",
                        "private String drawingUrl;",
                        "private String internalProjectNo;"
                );
        assertThat(read(MES_RESP))
                .contains(
                        "class MesMaterialResolveResp",
                        "private Boolean success;",
                        "private String materialNo;",
                        "private String message;",
                        "private String externalTraceId;"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
