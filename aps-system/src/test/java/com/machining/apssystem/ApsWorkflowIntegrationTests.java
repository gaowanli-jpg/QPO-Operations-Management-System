package com.machining.apssystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.apssystem.dto.ApsDepartmentDTO;
import com.machining.apssystem.dto.ApsMachineDTO;
import com.machining.apssystem.dto.ApsMaterialInventoryDTO;
import com.machining.apssystem.dto.ApsMaterialLockDTO;
import com.machining.apssystem.dto.ApsMaterialShortageDTO;
import com.machining.apssystem.dto.ApsPartInventoryDTO;
import com.machining.apssystem.dto.ApsPartInventoryLockDTO;
import com.machining.apssystem.dto.ApsPartInventoryPendingStockDTO;
import com.machining.apssystem.dto.ApsPartProductionRequirementDTO;
import com.machining.apssystem.dto.ApsSyncResultDTO;
import com.machining.apssystem.dto.ApsWorkItemDTO;
import com.machining.apssystem.dto.ApsWorkItemPageDTO;
import com.machining.apssystem.dto.DepartmentSaveReq;
import com.machining.apssystem.dto.ErpDeliveryItemBatchDTO;
import com.machining.apssystem.dto.ErpDeliveryItemDTO;
import com.machining.apssystem.dto.MaterialInventorySaveReq;
import com.machining.apssystem.dto.MachineCapabilitySaveReq;
import com.machining.apssystem.dto.MachineSaveReq;
import com.machining.apssystem.dto.PartInventoryModifyReq;
import com.machining.apssystem.dto.PartInventorySaveReq;
import com.machining.apssystem.dto.PartInventoryStockOpReq;
import com.machining.apssystem.dto.WorkItemBulkAssignReq;
import com.machining.apssystem.dto.WorkItemBulkDeleteReq;
import com.machining.apssystem.entity.ApsMaterialInventory;
import com.machining.apssystem.entity.ApsPartInventory;
import com.machining.apssystem.entity.ApsProcess;
import com.machining.apssystem.mapper.ApsMaterialInventoryMapper;
import com.machining.apssystem.mapper.ApsPartInventoryMapper;
import com.machining.apssystem.mapper.ApsPartInventoryPendingStockMapper;
import com.machining.apssystem.mapper.ApsProcessMapper;
import com.machining.apssystem.mapper.ApsWorkItemMapper;
import com.machining.apssystem.service.ApsDepartmentService;
import com.machining.apssystem.service.ApsDepartmentCenterService;
import com.machining.apssystem.service.ApsInventoryService;
import com.machining.apssystem.service.ApsMachineCapabilityService;
import com.machining.apssystem.service.ApsPmcService;
import com.machining.apssystem.service.ApsSyncService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApsWorkflowIntegrationTests {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");
    private static final AtomicReference<String> ERP_RESPONSE = new AtomicReference<>("""
            {"success":true,"records":[],"count":0,"nextAfterTicketId":null,"hasMore":false}
            """);
    private static final HttpServer ERP_SERVER = startErpServer();

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("aps.erp.base-url", () -> "http://127.0.0.1:" + ERP_SERVER.getAddress().getPort());
        registry.add("aps.erp.auth-token", () -> "");
        registry.add("aps.auth.enabled", () -> false);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApsInventoryService inventoryService;

    @Autowired
    private ApsDepartmentService departmentService;

    @Autowired
    private ApsDepartmentCenterService departmentCenterService;

    @Autowired
    private ApsMachineCapabilityService machineCapabilityService;

    @Autowired
    private ApsSyncService syncService;

    @Autowired
    private ApsPmcService pmcService;

    @Autowired
    private ApsPartInventoryMapper partInventoryMapper;

    @Autowired
    private ApsPartInventoryPendingStockMapper pendingStockMapper;

    @Autowired
    private ApsMaterialInventoryMapper materialInventoryMapper;

    @Autowired
    private ApsProcessMapper processMapper;

    @Autowired
    private ApsWorkItemMapper workItemMapper;

    @BeforeEach
    void setUp() {
        ERP_RESPONSE.set("""
                {"success":true,"records":[],"count":0,"nextAfterTicketId":null,"hasMore":false}
                """);
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    aps_department_task,
                    aps_department_assignment,
                    aps_machine_process_capability,
                    aps_machine,
                    aps_process,
                    aps_department,
                    aps_material_shortage,
                    aps_material_lock,
                    aps_material_inventory,
                    aps_production_requirement,
                    aps_part_inventory_lock_detail,
                    aps_part_inventory_lock,
                    aps_part_inventory_pending_stock,
                    aps_part_inventory,
                    aps_work_item_process,
                    aps_work_item,
                    aps_erp_sync_run
                RESTART IDENTITY CASCADE
                """);
    }

    @AfterAll
    static void tearDown() {
        ERP_SERVER.stop(0);
        POSTGRES.stop();
    }

    @Test
    void duplicatePartAndMaterialCreateRejectWithoutOverwritingExistingStock() {
        PartInventorySaveReq partReq = new PartInventorySaveReq();
        partReq.setCustomerName("ACME");
        partReq.setPartNo("P-100");
        partReq.setVersion("A");
        partReq.setInStockQty(10);
        partReq.setInProductionQty(0);
        ApsPartInventoryDTO part = inventoryService.savePartInventory(partReq);

        PartInventorySaveReq duplicatePartReq = new PartInventorySaveReq();
        duplicatePartReq.setCustomerName("ACME");
        duplicatePartReq.setPartNo("P-100");
        duplicatePartReq.setVersion("A");
        duplicatePartReq.setInStockQty(1);
        duplicatePartReq.setInProductionQty(1);

        IllegalStateException partEx = assertThrows(IllegalStateException.class,
                () -> inventoryService.savePartInventory(duplicatePartReq));
        assertTrue(partEx.getMessage().contains("already exists"));

        ApsPartInventory savedPart = partInventoryMapper.selectById(part.getId());
        assertEquals(10, savedPart.getInStockQty());
        assertEquals(0, savedPart.getInProductionQty());

        MaterialInventorySaveReq materialReq = new MaterialInventorySaveReq();
        materialReq.setMaterialName("AL-6061");
        materialReq.setMaterialType("PLATE");
        materialReq.setStockWeightKg(new BigDecimal("5.0"));
        materialReq.setSafetyStockWeightKg(new BigDecimal("1.0"));
        materialReq.setUnit("kg");
        ApsMaterialInventoryDTO material = inventoryService.saveMaterialInventory(materialReq);

        MaterialInventorySaveReq duplicateMaterialReq = new MaterialInventorySaveReq();
        duplicateMaterialReq.setMaterialName("AL-6061");
        duplicateMaterialReq.setMaterialType("PLATE");
        duplicateMaterialReq.setStockWeightKg(new BigDecimal("1.0"));
        duplicateMaterialReq.setSafetyStockWeightKg(BigDecimal.ZERO);
        duplicateMaterialReq.setUnit("kg");

        IllegalStateException materialEx = assertThrows(IllegalStateException.class,
                () -> inventoryService.saveMaterialInventory(duplicateMaterialReq));
        assertTrue(materialEx.getMessage().contains("already exists"));

        ApsMaterialInventory savedMaterial = materialInventoryMapper.selectById(material.getId());
        assertEquals(0, savedMaterial.getStockWeightKg().compareTo(new BigDecimal("5.0")));
    }

    @Test
    void syncLocksInventoryAndDuplicateSyncIsSkipped() throws Exception {
        SeededInventory seeded = seedLockedInventory();
        setErpBatch(buildDeliveryItem(1001, 5, new BigDecimal("1.5000")));

        ApsSyncResultDTO first = syncService.syncErpDeliveryItems(50);
        assertEquals(1, first.getReceivedCount());
        assertEquals(1, first.getImportedCount());
        assertEquals(0, first.getSkippedCount());
        assertEquals(0, first.getProductionRequirementCount());
        assertEquals(0, first.getMaterialShortageCount());
        assertEquals(1, workItemMapper.countByErpTicketId(1001));
        assertEquals("CO-APS", first.getImportedItems().get(0).getCustomerOrderNo());
        assertEquals("", first.getImportedItems().get(0).getCustomerProjectNo());

        ApsPartInventory part = partInventoryMapper.selectById(seeded.partInventoryId());
        assertEquals(3, part.getLockedInStockQty());
        assertEquals(2, part.getLockedInProductionQty());

        List<ApsPartInventoryPendingStockDTO> pendingRows =
                pendingStockMapper.selectDtosByInventoryId(seeded.partInventoryId());
        assertEquals(1, pendingRows.size());
        assertEquals(2, pendingRows.get(0).getLockedQty());

        List<ApsPartInventoryLockDTO> lockRows = inventoryService.listPartLocks(null, 10);
        assertEquals(2, lockRows.size());
        ApsPartInventoryLockDTO inStockLock = findLockRow(lockRows, "IN_STOCK", null);
        assertNotNull(inStockLock);
        assertEquals("锁定已入库库存", inStockLock.getLockTypeText());
        assertEquals(3, inStockLock.getLockedQty());
        ApsPartInventoryLockDTO productionLock = findLockRow(lockRows, "IN_PRODUCTION", "WO-1");
        assertNotNull(productionLock);
        assertEquals("锁定在产库存", productionLock.getLockTypeText());
        assertEquals(2, productionLock.getLockedQty());

        var exportResult = mockMvc.perform(get("/api/aps/inventory/parts/locks/export")
                        .param("customerName", "ACME")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(exportResult.getResponse().getHeader("Content-Disposition").contains("filename*=UTF-8''"));
        assertTrue(exportResult.getResponse().getContentAsByteArray().length > 100);

        List<ApsWorkItemDTO> pendingTasks = pmcService.listPmcTasks("PENDING", 10);
        assertEquals(1, pendingTasks.size());
        assertEquals(0, pendingTasks.get(0).getQty());

        ApsWorkItemPageDTO pendingPage = pmcService.listPmcTaskPage("PENDING", 1, 20);
        assertEquals(1, pendingPage.getTotal());
        assertEquals(1, pendingPage.getPage());
        assertEquals(20, pendingPage.getPageSize());
        assertEquals(1, pendingPage.getRecords().size());

        ApsWorkItemPageDTO assignedPage = pmcService.listPmcTaskPage("ASSIGNED", 1, 20);
        assertEquals(0, assignedPage.getTotal());
        assertEquals(0, assignedPage.getRecords().size());

        inventoryService.confirmPartLock(productionLock.getId());

        part = partInventoryMapper.selectById(seeded.partInventoryId());
        assertEquals(3, part.getLockedInStockQty());
        assertEquals(2, part.getLockedInProductionQty());

        lockRows = inventoryService.listPartLocks(null, 10);
        assertEquals(1, lockRows.size());
        assertEquals("IN_STOCK", lockRows.get(0).getLockType());

        assertEquals(1, inventoryService.listPartLocks("ACME", 10).size());
        assertEquals(0, inventoryService.listPartLocks("NO_SUCH_CUSTOMER", 10).size());

        inventoryService.releasePartLock(inStockLock.getId());

        part = partInventoryMapper.selectById(seeded.partInventoryId());
        assertEquals(0, part.getLockedInStockQty());
        assertEquals(2, part.getLockedInProductionQty());

        assertEquals(0, inventoryService.listPartLocks(null, 10).size());

        List<ApsPartProductionRequirementDTO> productionRequirements =
                inventoryService.listProductionRequirements("PENDING_PRODUCTION", 10);
        assertEquals(1, productionRequirements.size());
        assertEquals(3, productionRequirements.get(0).getRequiredQty());

        pendingTasks = pmcService.listPmcTasks("PENDING", 10);
        assertEquals(1, pendingTasks.size());
        assertEquals(3, pendingTasks.get(0).getQty());
        assertEquals("PARTIAL_LOCKED", pendingTasks.get(0).getPartStockStatus());

        ApsMaterialInventory material = materialInventoryMapper.selectById(seeded.materialInventoryId());
        assertEquals(0, material.getLockedWeightKg().compareTo(new BigDecimal("1.5000")));

        List<ApsMaterialLockDTO> materialLockRows = inventoryService.listMaterialLocks(null, 10);
        assertEquals(1, materialLockRows.size());
        assertEquals(0, materialLockRows.get(0).getLockedWeightKg().compareTo(new BigDecimal("1.5000")));
        assertEquals(1, inventoryService.listMaterialLocks("ACME", 10).size());
        assertEquals(0, inventoryService.listMaterialLocks("NO_SUCH_CUSTOMER", 10).size());

        inventoryService.confirmMaterialLock(materialLockRows.get(0).getId());
        assertEquals(0, inventoryService.listMaterialLocks(null, 10).size());

        material = materialInventoryMapper.selectById(seeded.materialInventoryId());
        assertEquals(0, material.getLockedWeightKg().compareTo(new BigDecimal("1.5000")));

        ApsSyncResultDTO second = syncService.syncErpDeliveryItems(50);
        assertEquals(1, second.getReceivedCount());
        assertEquals(0, second.getImportedCount());
        assertEquals(1, second.getSkippedCount());
        assertEquals(1, workItemMapper.countByErpTicketId(1001));
    }

    @Test
    void pmcTaskPageSupportsCustomerFilterMaterialSortAndProcessGrouping() throws Exception {
        ErpDeliveryItemDTO first = buildDeliveryItem(1001, 1, BigDecimal.ZERO);
        first.setPartNo("P-100");
        first.setMaterialName("AL-6061");
        first.setMaterialType("PLATE");
        first.setProcessRows(List.of(
                processRow(1, "下料"),
                processRow(2, "CNC"),
                processRow(3, "检验")));

        ErpDeliveryItemDTO second = buildDeliveryItem(1002, 1, BigDecimal.ZERO);
        second.setPartNo("P-200");
        second.setMaterialName("BRASS");
        second.setMaterialType("BAR");
        second.setProcessRows(List.of(
                processRow(1, "下料"),
                processRow(2, "CNC"),
                processRow(3, "检验")));

        ErpDeliveryItemDTO third = buildDeliveryItem(1003, 1, BigDecimal.ZERO);
        third.setCustomerName("BETA");
        third.setPartNo("P-300");
        third.setMaterialName("AL-6061");
        third.setMaterialType("PLATE");
        third.setProcessRows(List.of(
                processRow(1, "下料"),
                processRow(2, "CNC"),
                processRow(3, "表处")));

        setErpBatch(first, second, third);
        ApsSyncResultDTO syncResult = syncService.syncErpDeliveryItems(50);
        assertEquals(3, syncResult.getImportedCount());

        assertEquals(List.of("ACME", "BETA"), pmcService.listTaskCustomerNames("PENDING"));

        ApsWorkItemPageDTO customerPage = pmcService.listPmcTaskPage("PENDING", "ACME", "", 1, 20);
        assertEquals(2, customerPage.getTotal());
        assertTrue(customerPage.getRecords().stream()
                .allMatch(task -> "ACME".equals(task.getCustomerName())));

        ApsWorkItemPageDTO materialPage = pmcService.listPmcTaskPage("PENDING", null, "MATERIAL", 1, 20);
        assertEquals(List.of("AL-6061", "AL-6061", "BRASS"), materialPage.getRecords().stream()
                .map(ApsWorkItemDTO::getMaterialName)
                .toList());

        ApsWorkItemPageDTO processPage = pmcService.listPmcTaskPage("PENDING", null, "PROCESS", 1, 20);
        assertEquals(3, processPage.getRecords().size());
        assertEquals(2, processPage.getRecords().get(0).getProcessGroupCount());
        assertEquals(2, processPage.getRecords().get(1).getProcessGroupCount());
        assertEquals(1, processPage.getRecords().get(2).getProcessGroupCount());
        assertEquals("下料 > CNC > 检验", processPage.getRecords().get(0).getProcessRouteText());
        assertNotNull(processPage.getRecords().get(0).getProcessRouteSignature());

        ApsWorkItemPageDTO processMatchPage = pmcService.listPmcTaskPage("PENDING", null, "PROCESS_MATCH", 1, 20);
        assertEquals(3, processMatchPage.getRecords().size());
        assertEquals(List.of(3, 3, 2), processMatchPage.getRecords().stream()
                .map(ApsWorkItemDTO::getProcessMatchCount)
                .toList());
        assertEquals("下料 > CNC > 表处", processMatchPage.getRecords().get(2).getProcessRouteText());

        var detail = pmcService.getTaskDetail(processPage.getRecords().get(0).getId());
        assertEquals("下料 > CNC > 检验", detail.getTask().getProcessRouteText());
    }

    @Test
    void bulkAssignDepartmentsAssignsSelectedPendingTasksInOneCall() throws Exception {
        ErpDeliveryItemDTO first = buildDeliveryItem(1001, 1, BigDecimal.ZERO);
        first.setPartNo("P-100");
        first.setTotalWorkHours(2.0);

        ErpDeliveryItemDTO second = buildDeliveryItem(1002, 1, BigDecimal.ZERO);
        second.setPartNo("P-200");
        second.setTotalWorkHours(3.0);

        setErpBatch(first, second);
        syncService.syncErpDeliveryItems(50);

        DepartmentSaveReq departmentReq = new DepartmentSaveReq();
        departmentReq.setDepartmentName("Batch Department");
        departmentReq.setDepartmentType("PRODUCTION");
        departmentReq.setDepartmentStatus("ENABLED");
        departmentReq.setMonthlyAvailableHours(new BigDecimal("20"));
        ApsDepartmentDTO department = departmentService.saveDepartment(departmentReq);

        List<Long> ids = pmcService.listPmcTaskPage("PENDING", 1, 20).getRecords().stream()
                .map(ApsWorkItemDTO::getId)
                .toList();
        assertEquals(2, ids.size());

        WorkItemBulkAssignReq req = new WorkItemBulkAssignReq();
        req.setIds(ids);
        req.setDepartmentId(department.getId());
        req.setAssignedBy("PMC");
        req.setRemark("bulk");

        Map<String, Object> result = pmcService.assignDepartments(req);
        assertEquals(true, result.get("success"));
        assertEquals(2, result.get("assignedCount"));

        ApsWorkItemPageDTO assignedPage = pmcService.listPmcTaskPage("ASSIGNED", 1, 20);
        assertEquals(2, assignedPage.getTotal());
        assertTrue(assignedPage.getRecords().stream()
                .allMatch(task -> department.getId().equals(task.getAssignedDepartmentId())));
        assertEquals(0, pmcService.listPmcTaskPage("PENDING", 1, 20).getTotal());

        ApsWorkItemPageDTO departmentTaskPage =
                departmentCenterService.listDepartmentTaskPage(department.getId(), "ACTIVE", 1, 20);
        assertEquals(2, departmentTaskPage.getTotal());
        assertTrue(departmentTaskPage.getRecords().stream()
                .allMatch(task -> department.getId().equals(task.getAssignedDepartmentId())));
        assertTrue(departmentTaskPage.getRecords().stream()
                .allMatch(task -> "ACTIVE".equals(task.getDepartmentTaskStatus())));

        ApsWorkItemPageDTO otherDepartmentTaskPage =
                departmentCenterService.listDepartmentTaskPage(department.getId() + 1, "ACTIVE", 1, 20);
        assertEquals(0, otherDepartmentTaskPage.getTotal());

        pmcService.releaseAssignment(ids.get(0));

        assertEquals(1, departmentCenterService.listDepartmentTaskPage(department.getId(), "ACTIVE", 1, 20).getTotal());
        ApsWorkItemPageDTO releasedDepartmentTaskPage =
                departmentCenterService.listDepartmentTaskPage(department.getId(), "RELEASED", 1, 20);
        assertEquals(1, releasedDepartmentTaskPage.getTotal());
        assertEquals("RELEASED", releasedDepartmentTaskPage.getRecords().get(0).getDepartmentTaskStatus());
    }

    @Test
    void materialLockReleaseRollsBackInventoryAndCreatesShortage() throws Exception {
        SeededInventory seeded = seedLockedInventory();
        setErpBatch(buildDeliveryItem(1001, 1, new BigDecimal("1.5000")));
        syncService.syncErpDeliveryItems(50);

        List<ApsMaterialLockDTO> materialLockRows = inventoryService.listMaterialLocks(null, 10);
        assertEquals(1, materialLockRows.size());

        inventoryService.releaseMaterialLock(materialLockRows.get(0).getId());

        ApsMaterialInventory material = materialInventoryMapper.selectById(seeded.materialInventoryId());
        assertEquals(0, material.getLockedWeightKg().compareTo(BigDecimal.ZERO));
        assertEquals(0, inventoryService.listMaterialLocks(null, 10).size());

        List<ApsMaterialShortageDTO> materialShortages =
                inventoryService.listMaterialShortages("PENDING_PURCHASE", 10);
        assertEquals(1, materialShortages.size());
        assertEquals(0, materialShortages.get(0).getShortageWeightKg().compareTo(new BigDecimal("1.5000")));

        List<ApsWorkItemDTO> pendingTasks = pmcService.listPmcTasks("PENDING", 10);
        assertEquals(1, pendingTasks.size());
        assertEquals("SHORTAGE", pendingTasks.get(0).getMaterialStockStatus());
    }

    @Test
    void partLockRowsSplitInStockAndEachPendingReference() throws Exception {
        PartInventorySaveReq partReq = new PartInventorySaveReq();
        partReq.setCustomerName("ACME");
        partReq.setPartNo("P-100");
        partReq.setVersion("A");
        partReq.setInStockQty(2);
        partReq.setInProductionQty(0);
        ApsPartInventoryDTO part = inventoryService.savePartInventory(partReq);

        PartInventoryStockOpReq firstPending = new PartInventoryStockOpReq();
        firstPending.setInventoryId(part.getId());
        firstPending.setStockType("PENDING");
        firstPending.setPendingRefNo("WO-1");
        firstPending.setQty(2);
        inventoryService.addPartStock(firstPending);

        PartInventoryStockOpReq secondPending = new PartInventoryStockOpReq();
        secondPending.setInventoryId(part.getId());
        secondPending.setStockType("PENDING");
        secondPending.setPendingRefNo("WO-2");
        secondPending.setQty(3);
        inventoryService.addPartStock(secondPending);

        setErpBatch(buildDeliveryItem(1001, 6, BigDecimal.ZERO));
        ApsSyncResultDTO syncResult = syncService.syncErpDeliveryItems(50);
        Long workItemId = syncResult.getImportedItems().get(0).getId();

        List<ApsPartInventoryLockDTO> lockRows = inventoryService.listPartLocks(null, 10);
        assertEquals(3, lockRows.size());
        assertEquals(2, findLockRow(lockRows, "IN_STOCK", null).getLockedQty());
        assertEquals(2, findLockRow(lockRows, "IN_PRODUCTION", "WO-1").getLockedQty());
        assertEquals(2, findLockRow(lockRows, "IN_PRODUCTION", "WO-2").getLockedQty());

        List<ApsPartInventoryPendingStockDTO> pendingRows =
                pendingStockMapper.selectDtosByInventoryId(part.getId());
        assertEquals(2, pendingRows.size());
        assertEquals(2, pendingRows.stream()
                .filter(row -> "WO-1".equals(row.getPendingRefNo()))
                .findFirst()
                .orElseThrow()
                .getLockedQty());
        assertEquals(2, pendingRows.stream()
                .filter(row -> "WO-2".equals(row.getPendingRefNo()))
                .findFirst()
                .orElseThrow()
                .getLockedQty());

        WorkItemBulkDeleteReq deleteReq = new WorkItemBulkDeleteReq();
        deleteReq.setIds(List.of(workItemId));
        pmcService.deleteWorkItems(deleteReq);

        pendingRows = pendingStockMapper.selectDtosByInventoryId(part.getId());
        assertEquals(0, pendingRows.stream().mapToInt(ApsPartInventoryPendingStockDTO::getLockedQty).sum());
        assertEquals(0, inventoryService.listPartLocks(null, 10).size());
    }

    @Test
    void deleteWorkItemsReleasesLocksAndInventoryDeleteUsesLockedChecks() throws Exception {
        SeededInventory seeded = seedLockedInventory();
        setErpBatch(buildDeliveryItem(1001, 5, new BigDecimal("1.5000")));
        ApsSyncResultDTO syncResult = syncService.syncErpDeliveryItems(50);
        Long workItemId = syncResult.getImportedItems().get(0).getId();

        assertThrows(IllegalStateException.class, () -> inventoryService.deletePartInventory(seeded.partInventoryId()));
        assertThrows(IllegalStateException.class, () -> inventoryService.deleteMaterialInventory(seeded.materialInventoryId()));

        WorkItemBulkDeleteReq deleteReq = new WorkItemBulkDeleteReq();
        deleteReq.setIds(List.of(workItemId));
        pmcService.deleteWorkItems(deleteReq);

        ApsPartInventory part = partInventoryMapper.selectById(seeded.partInventoryId());
        assertEquals(0, part.getLockedInStockQty());
        assertEquals(0, part.getLockedInProductionQty());

        List<ApsPartInventoryPendingStockDTO> pendingRows =
                pendingStockMapper.selectDtosByInventoryId(seeded.partInventoryId());
        assertEquals(1, pendingRows.size());
        assertEquals(0, pendingRows.get(0).getLockedQty());

        ApsMaterialInventory material = materialInventoryMapper.selectById(seeded.materialInventoryId());
        assertEquals(0, material.getLockedWeightKg().compareTo(BigDecimal.ZERO));
        assertEquals(0, workItemMapper.countByErpTicketId(1001));

        inventoryService.deletePartInventory(seeded.partInventoryId());
        inventoryService.deleteMaterialInventory(seeded.materialInventoryId());
        assertNull(partInventoryMapper.selectById(seeded.partInventoryId()));
        assertNull(materialInventoryMapper.selectById(seeded.materialInventoryId()));
    }

    @Test
    void inventoryCannotBeUpdatedBelowLockedQuantities() throws Exception {
        SeededInventory seeded = seedLockedInventory();
        setErpBatch(buildDeliveryItem(1001, 5, new BigDecimal("1.5000")));
        syncService.syncErpDeliveryItems(50);

        PartInventorySaveReq partUpdateReq = new PartInventorySaveReq();
        partUpdateReq.setCustomerName("ACME");
        partUpdateReq.setPartNo("P-100");
        partUpdateReq.setVersion("A");
        partUpdateReq.setInStockQty(2);
        partUpdateReq.setInProductionQty(2);

        IllegalStateException partUpdateEx = assertThrows(IllegalStateException.class,
                () -> inventoryService.updatePartInventory(seeded.partInventoryId(), partUpdateReq));
        assertTrue(partUpdateEx.getMessage().contains("locked in-stock quantity"));

        PartInventoryModifyReq pendingModifyReq = new PartInventoryModifyReq();
        pendingModifyReq.setInventoryId(seeded.partInventoryId());
        pendingModifyReq.setPendingRefNo("WO-1");
        pendingModifyReq.setPendingQty(1);

        IllegalStateException pendingEx = assertThrows(IllegalStateException.class,
                () -> inventoryService.modifyPartInventory(pendingModifyReq));
        assertTrue(pendingEx.getMessage().contains("locked pending quantity"));

        MaterialInventorySaveReq materialUpdateReq = new MaterialInventorySaveReq();
        materialUpdateReq.setMaterialName("AL-6061");
        materialUpdateReq.setMaterialType("PLATE");
        materialUpdateReq.setStockWeightKg(new BigDecimal("1.0000"));
        materialUpdateReq.setSafetyStockWeightKg(BigDecimal.ZERO);
        materialUpdateReq.setUnit("kg");

        IllegalStateException materialEx = assertThrows(IllegalStateException.class,
                () -> inventoryService.updateMaterialInventory(seeded.materialInventoryId(), materialUpdateReq));
        assertTrue(materialEx.getMessage().contains("locked weight"));
    }

    @Test
    void invalidInventoryRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/aps/inventory/parts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"customerName":"","partNo":"P-100","inStockQty":0,"inProductionQty":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("customerName is required."));
    }

    @Test
    void duplicateDepartmentUpdateReturnsConflictResponse() throws Exception {
        DepartmentSaveReq cutting = new DepartmentSaveReq();
        cutting.setDepartmentName("Cutting");
        cutting.setDepartmentType("PRODUCTION");
        cutting.setDepartmentStatus("ENABLED");
        cutting.setMonthlyAvailableHours(new BigDecimal("400"));
        departmentService.saveDepartment(cutting);

        DepartmentSaveReq assembly = new DepartmentSaveReq();
        assembly.setDepartmentName("Assembly");
        assembly.setDepartmentType("PRODUCTION");
        assembly.setDepartmentStatus("ENABLED");
        assembly.setMonthlyAvailableHours(new BigDecimal("320"));
        ApsDepartmentDTO savedAssembly = departmentService.saveDepartment(assembly);

        mockMvc.perform(put("/api/aps/pmc/departments/{id}", savedAssembly.getId())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"departmentName":"Cutting","departmentType":"PRODUCTION","departmentStatus":"ENABLED","monthlyAvailableHours":320}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Department name already exists."));
    }

    @Test
    void departmentMachineAndCapabilityCreatesAreCreateOnly() {
        DepartmentSaveReq departmentReq = new DepartmentSaveReq();
        departmentReq.setDepartmentName("Machining");
        departmentReq.setDepartmentType("PRODUCTION");
        departmentReq.setDepartmentStatus("ENABLED");
        departmentReq.setMonthlyAvailableHours(new BigDecimal("480"));
        ApsDepartmentDTO department = departmentService.saveDepartment(departmentReq);

        DepartmentSaveReq duplicateDepartmentReq = new DepartmentSaveReq();
        duplicateDepartmentReq.setDepartmentName("Machining");
        duplicateDepartmentReq.setDepartmentType("OUTSOURCE");
        duplicateDepartmentReq.setDepartmentStatus("DISABLED");
        duplicateDepartmentReq.setMonthlyAvailableHours(new BigDecimal("120"));

        IllegalStateException departmentEx = assertThrows(IllegalStateException.class,
                () -> departmentService.saveDepartment(duplicateDepartmentReq));
        assertTrue(departmentEx.getMessage().contains("already exists"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aps_department WHERE department_name = ?",
                Integer.class,
                "Machining"));

        MachineSaveReq machineReq = new MachineSaveReq();
        machineReq.setDepartmentId(department.getId());
        machineReq.setMachineCode("MC-01");
        machineReq.setMachineName("Mazak 01");
        machineReq.setMachineStatus("IDLE");
        ApsMachineDTO machine = machineCapabilityService.saveMachine(machineReq);

        MachineSaveReq duplicateMachineReq = new MachineSaveReq();
        duplicateMachineReq.setDepartmentId(department.getId());
        duplicateMachineReq.setMachineCode("MC-01");
        duplicateMachineReq.setMachineName("Mazak Override");
        duplicateMachineReq.setMachineStatus("RUNNING");

        IllegalStateException machineEx = assertThrows(IllegalStateException.class,
                () -> machineCapabilityService.saveMachine(duplicateMachineReq));
        assertTrue(machineEx.getMessage().contains("already exists"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aps_machine WHERE department_id = ? AND machine_code = ?",
                Integer.class,
                department.getId(),
                "MC-01"));

        ApsProcess process = seedProcess("Milling", "WORK_HOUR", "WORK_HOUR");

        MachineCapabilitySaveReq capabilityReq = new MachineCapabilitySaveReq();
        capabilityReq.setMachineId(machine.getId());
        capabilityReq.setProcessId(process.getId());
        machineCapabilityService.saveMachineCapability(capabilityReq);

        MachineCapabilitySaveReq duplicateCapabilityReq = new MachineCapabilitySaveReq();
        duplicateCapabilityReq.setMachineId(machine.getId());
        duplicateCapabilityReq.setProcessId(process.getId());

        IllegalStateException capabilityEx = assertThrows(IllegalStateException.class,
                () -> machineCapabilityService.saveMachineCapability(duplicateCapabilityReq));
        assertTrue(capabilityEx.getMessage().contains("already exists"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aps_machine_process_capability WHERE machine_id = ? AND process_name = ? AND calc_type = ?",
                Integer.class,
                machine.getId(),
                "Milling",
                "WORK_HOUR"));
    }

    private SeededInventory seedLockedInventory() {
        PartInventorySaveReq partReq = new PartInventorySaveReq();
        partReq.setCustomerName("ACME");
        partReq.setPartNo("P-100");
        partReq.setVersion("A");
        partReq.setInStockQty(3);
        partReq.setInProductionQty(0);
        ApsPartInventoryDTO part = inventoryService.savePartInventory(partReq);

        PartInventoryStockOpReq pendingReq = new PartInventoryStockOpReq();
        pendingReq.setInventoryId(part.getId());
        pendingReq.setStockType("PENDING");
        pendingReq.setPendingRefNo("WO-1");
        pendingReq.setQty(2);
        inventoryService.addPartStock(pendingReq);

        MaterialInventorySaveReq materialReq = new MaterialInventorySaveReq();
        materialReq.setMaterialName("AL-6061");
        materialReq.setMaterialType("PLATE");
        materialReq.setStockWeightKg(new BigDecimal("2.0000"));
        materialReq.setSafetyStockWeightKg(BigDecimal.ZERO);
        materialReq.setUnit("kg");
        ApsMaterialInventoryDTO material = inventoryService.saveMaterialInventory(materialReq);

        return new SeededInventory(part.getId(), material.getId());
    }

    private ErpDeliveryItemDTO buildDeliveryItem(int ticketId, int qty, BigDecimal materialDemandWeight) {
        ErpDeliveryItemDTO item = new ErpDeliveryItemDTO();
        item.setTicketId(ticketId);
        item.setRfqId(2001);
        item.setItemId(3001);
        item.setCustomerName("ACME");
        item.setInternalProjectNo("PRJ-APS");
        item.setCustomerOrderNo("CO-APS");
        item.setCustomerProjectNo("");
        item.setPartNo("P-100");
        item.setVersion("A");
        item.setQty(qty);
        item.setUnit("pcs");
        item.setMaterialName("AL-6061");
        item.setMaterialType("PLATE");
        item.setMaterialDemandWeight(materialDemandWeight.doubleValue());
        item.setMaterialGrossWeight(materialDemandWeight.doubleValue());
        item.setProcessRows(List.of());
        return item;
    }

    private ErpDeliveryItemDTO.ProcessRow processRow(Integer sortNo, String processName) {
        ErpDeliveryItemDTO.ProcessRow row = new ErpDeliveryItemDTO.ProcessRow();
        row.setSortNo(sortNo);
        row.setProcessName(processName);
        return row;
    }

    private ApsProcess seedProcess(String processName, String processType, String calcType) {
        ApsProcess process = new ApsProcess();
        process.setProcessName(processName);
        process.setProcessType(processType);
        process.setCalcType(calcType);
        processMapper.insert(process);
        return process;
    }

    private void setErpBatch(ErpDeliveryItemDTO... items) throws Exception {
        ErpDeliveryItemBatchDTO batch = new ErpDeliveryItemBatchDTO();
        batch.setSuccess(true);
        batch.setRecords(List.of(items));
        batch.setCount(items.length);
        batch.setNextAfterTicketId(items.length == 0 ? null : items[items.length - 1].getTicketId());
        batch.setHasMore(false);
        ERP_RESPONSE.set(objectMapper.writeValueAsString(batch));
    }

    private ApsPartInventoryLockDTO findLockRow(
            List<ApsPartInventoryLockDTO> rows,
            String lockType,
            String pendingRefNo) {
        return rows.stream()
                .filter(row -> lockType.equals(row.getLockType()))
                .filter(row -> pendingRefNo == null || pendingRefNo.equals(row.getPendingRefNo()))
                .findFirst()
                .orElse(null);
    }

    private static HttpServer startErpServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api/aps/source/delivery-items", ApsWorkflowIntegrationTests::handleErpRequest);
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start ERP test server.", ex);
        }
    }

    private static void handleErpRequest(HttpExchange exchange) throws IOException {
        byte[] payload = ERP_RESPONSE.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        } finally {
            exchange.close();
        }
    }

    private record SeededInventory(Long partInventoryId, Long materialInventoryId) {
    }
}
