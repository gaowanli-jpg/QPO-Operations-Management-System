package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.ProcessArrangementItemBatchReq;
import com.machining.quote.dto.ProcessArrangementItemMoveReq;
import com.machining.quote.dto.ProcessArrangementItemReq;
import com.machining.quote.dto.ProcessArrangementNodeSaveReq;
import com.machining.quote.entity.DictExtraFeeItem;
import com.machining.quote.entity.DictProcessArrangementItem;
import com.machining.quote.entity.DictProcessArrangementNode;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.mapper.DictProcessArrangementItemMapper;
import com.machining.quote.mapper.DictProcessArrangementNodeMapper;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.DictProcessDetailMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.ProcessArrangementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessArrangementBackendServiceTest {

    @Test
    void migrationEntitiesControllerPermissionsAndAuditLabelsExist() throws Exception {
        String migration = read(Path.of("src", "main", "resources", "db", "migration", "V35__process_arrangement.sql"));
        String controller = readSource("controller/ProcessArrangementController.java");

        assertThat(migration)
                .contains(
                        "CREATE TABLE IF NOT EXISTS dict_process_arrangement_node",
                        "CREATE TABLE IF NOT EXISTS dict_process_arrangement_item",
                        "parent_node_id BIGINT",
                        "source_type VARCHAR(20) NOT NULL",
                        "process_detail_id BIGINT",
                        "fee_item_id INTEGER",
                        "uk_dict_process_arrangement_item_process",
                        "uk_dict_process_arrangement_item_extra")
                .doesNotContain("INSERT INTO dict_process_arrangement");
        assertThat(readSource("entity/DictProcessArrangementNode.java"))
                .contains("@TableName(\"dict_process_arrangement_node\")", "private Long parentId;", "private String nodeName;");
        assertThat(readSource("entity/DictProcessArrangementItem.java"))
                .contains("@TableName(\"dict_process_arrangement_item\")", "private String sourceType;", "private Long processDetailId;", "private Integer feeItemId;");
        assertThat(readSource("mapper/DictProcessArrangementNodeMapper.java"))
                .contains("extends BaseMapper<DictProcessArrangementNode>");
        assertThat(readSource("mapper/DictProcessArrangementItemMapper.java"))
                .contains("extends BaseMapper<DictProcessArrangementItem>");
        assertThat(controller)
                .contains(
                        "@RequestMapping(\"/api/dict/process-arrangement\")",
                        "@GetMapping(\"/tree\")",
                        "@GetMapping(\"/unplaced-options\")",
                        "@PostMapping(\"/nodes\")",
                        "@PutMapping(\"/nodes/{id}\")",
                        "@DeleteMapping(\"/nodes/{id}\")",
                        "@PostMapping(\"/items/batch\")",
                        "@PutMapping(\"/items/{id}/move\")",
                        "@DeleteMapping(\"/items/{id}\")",
                        "@RequirePermission(\"engineer:dict:view\")",
                        "@RequirePermission(\"engineer:dict:process-manage\")");
        assertThat(AuditService.inferModule("DICT_PROCESS_ARRANGEMENT_NODE_CREATE")).isEqualTo("DICT");
        assertThat(AuditService.labelForAction("DICT_PROCESS_ARRANGEMENT_NODE_CREATE")).contains("工艺编排");
        assertThat(AuditService.labelForAction("DICT_PROCESS_ARRANGEMENT_ITEM_PLACE")).contains("工艺编排");
    }

    @Test
    void treeBuildsMixedContainerAndLeafHierarchy() {
        Fixture fixture = fixture();
        DictProcessArrangementNode root = node(1L, null, "铣", 1);
        DictProcessArrangementNode child = node(2L, 1L, "粗铣", 1);
        DictProcessArrangementItem processLeaf = processItem(10L, 1L, 100L, 2);
        DictProcessArrangementItem extraLeaf = extraItem(11L, 2L, 5, 1);
        when(fixture.nodeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(root, child));
        when(fixture.itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(processLeaf, extraLeaf));
        when(fixture.detailMapper.selectBatchIds(List.of(100L))).thenReturn(List.of(detail(100L, 7L, "精铣", true)));
        when(fixture.categoryMapper.selectBatchIds(List.of(7L))).thenReturn(List.of(category(7L, true)));
        when(fixture.extraFeeItemMapper.selectBatchIds(List.of(5))).thenReturn(List.of(extraFee(5, "EDM", "EDM", true)));

        var tree = fixture.service.listTree();

        assertThat(tree).singleElement().satisfies(rootRow -> {
            assertThat(rootRow.getNodeType()).isEqualTo("node");
            assertThat(rootRow.getLabel()).isEqualTo("铣");
            assertThat(rootRow.getChildren()).extracting("label").containsExactly("粗铣", "精铣（MILL / 铣 / 88.00元/小时）");
            assertThat(rootRow.getChildren().get(0).getChildren()).singleElement().satisfies(extraRow -> {
                assertThat(extraRow.getNodeType()).isEqualTo("item");
                assertThat(extraRow.getSourceType()).isEqualTo("extra");
                assertThat(extraRow.getLabel()).isEqualTo("EDM（非工时费用）");
            });
        });
        verify(fixture.auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void unplacedOptionsExcludeAlreadyPlacedProcessAndExtraRows() {
        Fixture fixture = fixture();
        when(fixture.itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                processItem(10L, null, 100L, 1),
                extraItem(11L, null, 5, 1)));
        when(fixture.detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                detail(100L, 7L, "已编排工艺", true),
                detail(101L, 7L, "未编排工艺", true)));
        when(fixture.categoryMapper.selectBatchIds(List.of(7L))).thenReturn(List.of(category(7L, true)));
        when(fixture.extraFeeItemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                extraFee(5, "EDM", "已编排EDM", true),
                extraFee(6, "QA", "未编排QA", false)));

        var options = fixture.service.listUnplacedOptions(null, null);

        assertThat(options).extracting("optionKey").containsExactly("process:101", "extra:6");
        assertThat(options.get(1).getEnabled()).isFalse();
        verify(fixture.auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void createNodeValidatesParentNameAndRecordsSummaryAudit() {
        Fixture fixture = fixture();
        when(fixture.nodeMapper.selectById(1L)).thenReturn(node(1L, null, "铣", 1));
        when(fixture.nodeMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            DictProcessArrangementNode entity = invocation.getArgument(0);
            entity.setId(2L);
            return 1;
        }).when(fixture.nodeMapper).insert(any(DictProcessArrangementNode.class));
        ProcessArrangementNodeSaveReq req = new ProcessArrangementNodeSaveReq();
        req.setParentId(1L);
        req.setNodeName(" 粗铣 ");
        req.setSortNo(3);

        var created = fixture.service.createNode(req);

        assertThat(created.getId()).isEqualTo(2L);
        assertThat(created.getParentId()).isEqualTo(1L);
        assertThat(created.getLabel()).isEqualTo("粗铣");
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(eq("DICT_PROCESS_ARRANGEMENT_NODE_CREATE"), eq("dict_process_arrangement_node"), eq(2L), isNull(), afterCaptor.capture());
        assertThat(String.valueOf(afterCaptor.getValue())).contains("nodeName=粗铣", "parentId=1");
    }

    @Test
    void moveNodeRejectsCycles() {
        Fixture fixture = fixture();
        when(fixture.nodeMapper.selectById(1L)).thenReturn(node(1L, null, "铣", 1));
        when(fixture.nodeMapper.selectById(2L)).thenReturn(node(2L, 1L, "粗铣", 1));
        ProcessArrangementNodeSaveReq req = new ProcessArrangementNodeSaveReq();
        req.setParentId(2L);
        req.setNodeName("铣");

        assertThatThrownBy(() -> fixture.service.updateNode(1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能移动到自己的下级");
        verify(fixture.nodeMapper, never()).updateById(any(DictProcessArrangementNode.class));
    }

    @Test
    void placeItemsRejectsExistingPlacementAndLeavesBatchUnchanged() {
        Fixture fixture = fixture();
        when(fixture.nodeMapper.selectById(1L)).thenReturn(node(1L, null, "铣", 1));
        when(fixture.detailMapper.selectById(100L)).thenReturn(detail(100L, 7L, "精铣", true));
        when(fixture.itemMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        ProcessArrangementItemBatchReq req = batch(1L, processRef(100L));

        assertThatThrownBy(() -> fixture.service.placeItems(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已在工艺编排中");
        verify(fixture.itemMapper, never()).insert(any(DictProcessArrangementItem.class));
    }

    @Test
    void placeItemsInsertsProcessAndExtraLeavesInOneAuditedBatch() {
        Fixture fixture = fixture();
        when(fixture.nodeMapper.selectById(1L)).thenReturn(node(1L, null, "铣", 1));
        when(fixture.detailMapper.selectById(100L)).thenReturn(detail(100L, 7L, "精铣", true));
        when(fixture.extraFeeItemMapper.selectById(5)).thenReturn(extraFee(5, "EDM", "EDM", true));
        when(fixture.itemMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(fixture.itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        doAnswer(invocation -> {
            DictProcessArrangementItem entity = invocation.getArgument(0);
            entity.setId(entity.getSourceType().equals("process") ? 20L : 21L);
            return 1;
        }).when(fixture.itemMapper).insert(any(DictProcessArrangementItem.class));

        var placed = fixture.service.placeItems(batch(1L, processRef(100L), extraRef(5)));

        assertThat(placed).hasSize(2);
        ArgumentCaptor<DictProcessArrangementItem> itemCaptor = ArgumentCaptor.forClass(DictProcessArrangementItem.class);
        verify(fixture.itemMapper, org.mockito.Mockito.times(2)).insert(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues()).extracting("sourceType").containsExactly("process", "extra");
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(eq("DICT_PROCESS_ARRANGEMENT_ITEM_PLACE"), eq("dict_process_arrangement_item"), eq("20,21"), isNull(), afterCaptor.capture());
        assertThat(String.valueOf(afterCaptor.getValue())).contains("parentNodeId=1", "itemCount=2");
    }

    @Test
    void placeItemsAppendsAfterContainerNodesAndLeavesInTheSameParent() {
        Fixture fixture = fixture();
        when(fixture.nodeMapper.selectById(1L)).thenReturn(node(1L, null, "铣", 1));
        when(fixture.detailMapper.selectById(100L)).thenReturn(detail(100L, 7L, "精铣", true));
        when(fixture.itemMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(fixture.itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(processItem(20L, 1L, 99L, 4)));
        when(fixture.nodeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(node(2L, 1L, "粗铣", 8)));
        doAnswer(invocation -> {
            DictProcessArrangementItem entity = invocation.getArgument(0);
            entity.setId(21L);
            return 1;
        }).when(fixture.itemMapper).insert(any(DictProcessArrangementItem.class));

        fixture.service.placeItems(batch(1L, processRef(100L)));

        ArgumentCaptor<DictProcessArrangementItem> itemCaptor = ArgumentCaptor.forClass(DictProcessArrangementItem.class);
        verify(fixture.itemMapper).insert(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getSortNo()).isEqualTo(9);
    }

    @Test
    void moveItemChangesParentAndSortAndAuditsBeforeAfter() {
        Fixture fixture = fixture();
        DictProcessArrangementItem before = processItem(20L, 1L, 100L, 1);
        when(fixture.itemMapper.selectById(20L)).thenReturn(before);
        when(fixture.nodeMapper.selectById(2L)).thenReturn(node(2L, 1L, "粗铣", 1));
        ProcessArrangementItemMoveReq req = new ProcessArrangementItemMoveReq();
        req.setParentNodeId(2L);
        req.setSortNo(9);

        var moved = fixture.service.moveItem(20L, req);

        assertThat(moved.getParentNodeId()).isEqualTo(2L);
        assertThat(moved.getSortNo()).isEqualTo(9);
        verify(fixture.itemMapper).updateById(any(DictProcessArrangementItem.class));
        verify(fixture.auditService).record(eq("DICT_PROCESS_ARRANGEMENT_ITEM_MOVE"), eq("dict_process_arrangement_item"), eq(20L), any(), any());
    }

    @Test
    void deleteNodeCascadesArrangementOnlyAndReturnsBaseProcessesToUnplacedPool() {
        Fixture fixture = fixture();
        DictProcessArrangementNode root = node(1L, null, "铣", 1);
        DictProcessArrangementNode child = node(2L, 1L, "粗铣", 1);
        when(fixture.nodeMapper.selectById(1L)).thenReturn(root);
        when(fixture.nodeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(root, child));
        when(fixture.itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                processItem(20L, 1L, 100L, 1),
                extraItem(21L, 2L, 5, 1)));

        var summary = fixture.service.deleteNode(1L);

        assertThat(summary.getRemovedNodeCount()).isEqualTo(2);
        assertThat(summary.getRemovedItemCount()).isEqualTo(2);
        verify(fixture.itemMapper).delete(any(QueryWrapper.class));
        verify(fixture.nodeMapper).deleteBatchIds(List.of(1L, 2L));
        verify(fixture.detailMapper, never()).deleteById(any(Long.class));
        verify(fixture.extraFeeItemMapper, never()).deleteById(any(Integer.class));
        verify(fixture.auditService).record(eq("DICT_PROCESS_ARRANGEMENT_NODE_DELETE"), eq("dict_process_arrangement_node"), eq(1L), any(), isNull());
    }

    private static Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.nodeMapper = mock(DictProcessArrangementNodeMapper.class);
        fixture.itemMapper = mock(DictProcessArrangementItemMapper.class);
        fixture.detailMapper = mock(DictProcessDetailMapper.class);
        fixture.categoryMapper = mock(DictProcessCategoryMapper.class);
        fixture.extraFeeItemMapper = mock(DictExtraFeeItemMapper.class);
        fixture.auditService = mock(AuditService.class);
        fixture.service = new ProcessArrangementService();
        ReflectionTestUtils.setField(fixture.service, "nodeMapper", fixture.nodeMapper);
        ReflectionTestUtils.setField(fixture.service, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(fixture.service, "detailMapper", fixture.detailMapper);
        ReflectionTestUtils.setField(fixture.service, "categoryMapper", fixture.categoryMapper);
        ReflectionTestUtils.setField(fixture.service, "extraFeeItemMapper", fixture.extraFeeItemMapper);
        ReflectionTestUtils.setField(fixture.service, "auditService", fixture.auditService);
        return fixture;
    }

    private static ProcessArrangementItemBatchReq batch(Long parentNodeId, ProcessArrangementItemReq... refs) {
        ProcessArrangementItemBatchReq req = new ProcessArrangementItemBatchReq();
        req.setParentNodeId(parentNodeId);
        req.setItems(List.of(refs));
        return req;
    }

    private static ProcessArrangementItemReq processRef(Long processDetailId) {
        ProcessArrangementItemReq req = new ProcessArrangementItemReq();
        req.setSourceType("process");
        req.setProcessDetailId(processDetailId);
        return req;
    }

    private static ProcessArrangementItemReq extraRef(Integer feeItemId) {
        ProcessArrangementItemReq req = new ProcessArrangementItemReq();
        req.setSourceType("extra");
        req.setFeeItemId(feeItemId);
        return req;
    }

    private static DictProcessArrangementNode node(Long id, Long parentId, String name, Integer sortNo) {
        DictProcessArrangementNode node = new DictProcessArrangementNode();
        node.setId(id);
        node.setParentId(parentId);
        node.setNodeName(name);
        node.setSortNo(sortNo);
        return node;
    }

    private static DictProcessArrangementItem processItem(Long id, Long parentNodeId, Long processDetailId, Integer sortNo) {
        DictProcessArrangementItem item = new DictProcessArrangementItem();
        item.setId(id);
        item.setParentNodeId(parentNodeId);
        item.setSourceType("process");
        item.setProcessDetailId(processDetailId);
        item.setSortNo(sortNo);
        return item;
    }

    private static DictProcessArrangementItem extraItem(Long id, Long parentNodeId, Integer feeItemId, Integer sortNo) {
        DictProcessArrangementItem item = new DictProcessArrangementItem();
        item.setId(id);
        item.setParentNodeId(parentNodeId);
        item.setSourceType("extra");
        item.setFeeItemId(feeItemId);
        item.setSortNo(sortNo);
        return item;
    }

    private static DictProcessDetail detail(Long id, Long categoryId, String name, boolean enabled) {
        DictProcessDetail detail = new DictProcessDetail();
        detail.setId(id);
        detail.setCategoryId(categoryId);
        detail.setDetailName(name);
        detail.setHourlyRate(null);
        detail.setEnabled(enabled);
        detail.setSortNo(1);
        return detail;
    }

    private static DictProcessCategory category(Long id, boolean enabled) {
        DictProcessCategory category = new DictProcessCategory();
        category.setId(id);
        category.setProcessCode("MILL");
        category.setCategoryName("铣");
        category.setHourlyRate(new BigDecimal("88.00"));
        category.setEnabled(enabled);
        category.setSortNo(1);
        return category;
    }

    private static DictExtraFeeItem extraFee(Integer id, String code, String name, boolean enabled) {
        DictExtraFeeItem item = new DictExtraFeeItem();
        item.setId(id);
        item.setFeeCode(code);
        item.setFeeName(name);
        item.setEnabled(enabled);
        item.setSortNo(1);
        return item;
    }

    private static String readSource(String relativePath) throws Exception {
        return read(Path.of("src", "main", "java", "com", "machining", "quote").resolve(relativePath));
    }

    private static String read(Path path) throws Exception {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static class Fixture {
        ProcessArrangementService service;
        DictProcessArrangementNodeMapper nodeMapper;
        DictProcessArrangementItemMapper itemMapper;
        DictProcessDetailMapper detailMapper;
        DictProcessCategoryMapper categoryMapper;
        DictExtraFeeItemMapper extraFeeItemMapper;
        AuditService auditService;
    }
}
