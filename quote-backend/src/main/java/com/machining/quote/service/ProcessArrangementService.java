package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.ProcessArrangementDeleteSummaryDTO;
import com.machining.quote.dto.ProcessArrangementItemBatchReq;
import com.machining.quote.dto.ProcessArrangementItemMoveReq;
import com.machining.quote.dto.ProcessArrangementItemReq;
import com.machining.quote.dto.ProcessArrangementNodeSaveReq;
import com.machining.quote.dto.ProcessArrangementOptionDTO;
import com.machining.quote.dto.ProcessArrangementTreeNodeDTO;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProcessArrangementService {
    private static final String NODE_TYPE_NODE = "node";
    private static final String NODE_TYPE_ITEM = "item";
    private static final String SOURCE_PROCESS = "process";
    private static final String SOURCE_EXTRA = "extra";

    @Autowired
    private DictProcessArrangementNodeMapper nodeMapper;
    @Autowired
    private DictProcessArrangementItemMapper itemMapper;
    @Autowired
    private DictProcessDetailMapper detailMapper;
    @Autowired
    private DictProcessCategoryMapper categoryMapper;
    @Autowired
    private DictExtraFeeItemMapper extraFeeItemMapper;
    @Autowired
    private AuditService auditService;

    public List<ProcessArrangementTreeNodeDTO> listTree() {
        List<DictProcessArrangementNode> nodes = allNodes();
        List<DictProcessArrangementItem> items = allItems();
        Map<Long, ProcessArrangementTreeNodeDTO> nodeDtos = nodes.stream()
                .map(this::toNodeDTO)
                .collect(Collectors.toMap(ProcessArrangementTreeNodeDTO::getNodeId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<ProcessArrangementTreeNodeDTO> roots = new ArrayList<>();

        for (DictProcessArrangementNode node : nodes) {
            ProcessArrangementTreeNodeDTO dto = nodeDtos.get(node.getId());
            ProcessArrangementTreeNodeDTO parent = node.getParentId() == null ? null : nodeDtos.get(node.getParentId());
            if (parent == null) {
                roots.add(dto);
            } else {
                parent.getChildren().add(dto);
            }
        }

        for (ProcessArrangementTreeNodeDTO item : toItemTreeNodes(items)) {
            ProcessArrangementTreeNodeDTO parent = item.getParentNodeId() == null ? null : nodeDtos.get(item.getParentNodeId());
            if (parent == null) {
                roots.add(item);
            } else {
                parent.getChildren().add(item);
            }
        }

        sortTree(roots);
        return roots;
    }

    public List<ProcessArrangementOptionDTO> listUnplacedOptions(String keyword, String sourceType) {
        String normalizedSourceType = normalizeOptionalSourceType(sourceType);
        Set<Long> placedProcessIds = new HashSet<>();
        Set<Integer> placedFeeIds = new HashSet<>();
        for (DictProcessArrangementItem item : allItems()) {
            if (SOURCE_PROCESS.equals(item.getSourceType()) && item.getProcessDetailId() != null) {
                placedProcessIds.add(item.getProcessDetailId());
            }
            if (SOURCE_EXTRA.equals(item.getSourceType()) && item.getFeeItemId() != null) {
                placedFeeIds.add(item.getFeeItemId());
            }
        }

        List<ProcessArrangementOptionDTO> options = new ArrayList<>();
        if (normalizedSourceType == null || SOURCE_PROCESS.equals(normalizedSourceType)) {
            List<DictProcessDetail> details = detailMapper.selectList(processDetailQuery(keyword));
            List<DictProcessDetail> unplaced = details.stream()
                    .filter(detail -> detail.getId() != null && !placedProcessIds.contains(detail.getId()))
                    .toList();
            options.addAll(toProcessOptions(unplaced));
        }
        if (normalizedSourceType == null || SOURCE_EXTRA.equals(normalizedSourceType)) {
            List<DictExtraFeeItem> fees = extraFeeItemMapper.selectList(extraFeeQuery(keyword));
            options.addAll(fees.stream()
                    .filter(item -> item.getId() != null && !placedFeeIds.contains(item.getId()))
                    .map(this::toExtraOption)
                    .toList());
        }
        return options.stream()
                .sorted(Comparator.comparing(ProcessArrangementOptionDTO::getSourceType)
                        .reversed()
                        .thenComparing(option -> Objects.toString(option.getLabel(), "")))
                .toList();
    }

    @Transactional
    public ProcessArrangementTreeNodeDTO createNode(ProcessArrangementNodeSaveReq req) {
        DictProcessArrangementNode entity = normalizeNode(req, null);
        requireParent(entity.getParentId());
        ensureNodeNameUnique(entity.getParentId(), entity.getNodeName(), null);
        nodeMapper.insert(entity);
        auditService.record(
                "DICT_PROCESS_ARRANGEMENT_NODE_CREATE",
                "dict_process_arrangement_node",
                entity.getId(),
                null,
                nodeAuditSummary(entity)
        );
        return toNodeDTO(entity);
    }

    @Transactional
    public ProcessArrangementTreeNodeDTO updateNode(Long id, ProcessArrangementNodeSaveReq req) {
        DictProcessArrangementNode before = requireNode(id);
        DictProcessArrangementNode entity = normalizeNode(req, before);
        entity.setId(id);
        ensureNodeHasNoCycle(id, entity.getParentId());
        ensureNodeNameUnique(entity.getParentId(), entity.getNodeName(), id);
        nodeMapper.updateById(entity);
        auditService.record(
                "DICT_PROCESS_ARRANGEMENT_NODE_UPDATE",
                "dict_process_arrangement_node",
                id,
                nodeAuditSummary(before),
                nodeAuditSummary(entity)
        );
        return toNodeDTO(entity);
    }

    @Transactional
    public ProcessArrangementDeleteSummaryDTO deleteNode(Long id) {
        DictProcessArrangementNode root = requireNode(id);
        List<DictProcessArrangementNode> descendants = descendantsOf(id, allNodes());
        List<Long> nodeIds = descendants.stream().map(DictProcessArrangementNode::getId).toList();
        List<DictProcessArrangementItem> removedItems = nodeIds.isEmpty()
                ? List.of()
                : itemMapper.selectList(new QueryWrapper<DictProcessArrangementItem>().in("parent_node_id", nodeIds));

        ProcessArrangementDeleteSummaryDTO summary = new ProcessArrangementDeleteSummaryDTO();
        summary.setRootNodeId(root.getId());
        summary.setRootNodeName(root.getNodeName());
        summary.setRemovedNodeCount(nodeIds.size());
        summary.setRemovedItemCount(removedItems.size());

        if (!nodeIds.isEmpty()) {
            itemMapper.delete(new QueryWrapper<DictProcessArrangementItem>().in("parent_node_id", nodeIds));
            nodeMapper.deleteBatchIds(nodeIds);
        }
        auditService.record(
                "DICT_PROCESS_ARRANGEMENT_NODE_DELETE",
                "dict_process_arrangement_node",
                id,
                Map.of(
                        "rootNodeId", root.getId(),
                        "rootNodeName", root.getNodeName(),
                        "removedNodeCount", summary.getRemovedNodeCount(),
                        "removedItemCount", summary.getRemovedItemCount()
                ),
                null
        );
        return summary;
    }

    @Transactional
    public List<ProcessArrangementTreeNodeDTO> placeItems(ProcessArrangementItemBatchReq req) {
        Long parentNodeId = req == null ? null : req.getParentNodeId();
        requireParent(parentNodeId);
        List<ProcessArrangementItemReq> refs = req == null || req.getItems() == null ? List.of() : req.getItems();
        if (refs.isEmpty()) {
            throw new IllegalArgumentException("请选择要放入的工艺");
        }
        ensureNoDuplicateRefs(refs);
        refs.forEach(this::validatePlaceableRef);
        refs.forEach(this::ensureItemNotPlaced);

        int nextSortNo = nextItemSortNo(parentNodeId);
        List<DictProcessArrangementItem> inserted = new ArrayList<>();
        for (int i = 0; i < refs.size(); i++) {
            ProcessArrangementItemReq ref = refs.get(i);
            DictProcessArrangementItem entity = new DictProcessArrangementItem();
            entity.setParentNodeId(parentNodeId);
            entity.setSourceType(normalizeRequiredSourceType(ref.getSourceType()));
            entity.setProcessDetailId(SOURCE_PROCESS.equals(entity.getSourceType()) ? ref.getProcessDetailId() : null);
            entity.setFeeItemId(SOURCE_EXTRA.equals(entity.getSourceType()) ? ref.getFeeItemId() : null);
            entity.setSortNo(ref.getSortNo() == null ? nextSortNo + i : ref.getSortNo());
            itemMapper.insert(entity);
            inserted.add(entity);
        }

        String targetId = inserted.stream().map(DictProcessArrangementItem::getId).map(String::valueOf).collect(Collectors.joining(","));
        auditService.record(
                "DICT_PROCESS_ARRANGEMENT_ITEM_PLACE",
                "dict_process_arrangement_item",
                targetId,
                null,
                Map.of(
                        "parentNodeId", parentNodeId,
                        "itemCount", inserted.size(),
                        "items", inserted.stream().map(this::itemAuditSummary).toList()
                )
        );
        return inserted.stream().map(this::toBareItemTreeNode).toList();
    }

    @Transactional
    public ProcessArrangementTreeNodeDTO moveItem(Long id, ProcessArrangementItemMoveReq req) {
        DictProcessArrangementItem before = requireItem(id);
        Long parentNodeId = req == null ? before.getParentNodeId() : req.getParentNodeId();
        requireParent(parentNodeId);
        DictProcessArrangementItem after = copyItem(before);
        after.setParentNodeId(parentNodeId);
        after.setSortNo(req == null || req.getSortNo() == null ? Objects.requireNonNullElse(before.getSortNo(), 0) : req.getSortNo());
        itemMapper.updateById(after);
        auditService.record(
                "DICT_PROCESS_ARRANGEMENT_ITEM_MOVE",
                "dict_process_arrangement_item",
                id,
                itemAuditSummary(before),
                itemAuditSummary(after)
        );
        return toBareItemTreeNode(after);
    }

    @Transactional
    public void removeItem(Long id) {
        DictProcessArrangementItem before = requireItem(id);
        itemMapper.deleteById(id);
        auditService.record(
                "DICT_PROCESS_ARRANGEMENT_ITEM_REMOVE",
                "dict_process_arrangement_item",
                id,
                itemAuditSummary(before),
                null
        );
    }

    private List<DictProcessArrangementNode> allNodes() {
        QueryWrapper<DictProcessArrangementNode> query = new QueryWrapper<>();
        query.orderByAsc("parent_id").orderByAsc("sort_no").orderByAsc("id");
        return nodeMapper.selectList(query);
    }

    private List<DictProcessArrangementItem> allItems() {
        QueryWrapper<DictProcessArrangementItem> query = new QueryWrapper<>();
        query.orderByAsc("parent_node_id").orderByAsc("sort_no").orderByAsc("id");
        return itemMapper.selectList(query);
    }

    private QueryWrapper<DictProcessDetail> processDetailQuery(String keyword) {
        QueryWrapper<DictProcessDetail> query = new QueryWrapper<>();
        if (hasText(keyword)) {
            String pattern = pattern(keyword);
            query.and(wrapper -> wrapper
                    .apply("LOWER(detail_name) LIKE {0}", pattern)
                    .or()
                    .apply("LOWER(detail_remarks) LIKE {0}", pattern)
                    .or()
                    .apply("EXISTS (SELECT 1 FROM dict_process_category c WHERE c.id = dict_process_detail.category_id AND (LOWER(c.process_code) LIKE {0} OR LOWER(c.category_name) LIKE {0}))", pattern));
        }
        query.orderByAsc("category_id").orderByAsc("sort_no").orderByAsc("id");
        return query;
    }

    private QueryWrapper<DictExtraFeeItem> extraFeeQuery(String keyword) {
        QueryWrapper<DictExtraFeeItem> query = new QueryWrapper<>();
        if (hasText(keyword)) {
            String pattern = pattern(keyword);
            query.and(wrapper -> wrapper
                    .apply("LOWER(fee_name) LIKE {0}", pattern)
                    .or()
                    .apply("LOWER(fee_code) LIKE {0}", pattern)
                    .or()
                    .apply("LOWER(remarks) LIKE {0}", pattern));
        }
        query.orderByAsc("sort_no").orderByAsc("id");
        return query;
    }

    private List<ProcessArrangementTreeNodeDTO> toItemTreeNodes(List<DictProcessArrangementItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<Long, DictProcessDetail> detailById = processDetailsById(items.stream()
                .filter(item -> SOURCE_PROCESS.equals(item.getSourceType()))
                .map(DictProcessArrangementItem::getProcessDetailId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        Map<Long, DictProcessCategory> categoryById = categoriesById(detailById.values().stream()
                .map(DictProcessDetail::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        Map<Integer, DictExtraFeeItem> feeById = extraFeesById(items.stream()
                .filter(item -> SOURCE_EXTRA.equals(item.getSourceType()))
                .map(DictProcessArrangementItem::getFeeItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return items.stream()
                .map(item -> {
                    DictProcessDetail detail = SOURCE_PROCESS.equals(item.getSourceType())
                            ? detailById.get(item.getProcessDetailId())
                            : null;
                    DictExtraFeeItem fee = SOURCE_EXTRA.equals(item.getSourceType())
                            ? feeById.get(item.getFeeItemId())
                            : null;
                    return toItemTreeNode(item, detail, fee, categoryById);
                })
                .toList();
    }

    private List<ProcessArrangementOptionDTO> toProcessOptions(List<DictProcessDetail> details) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        Map<Long, DictProcessCategory> categoryById = categoriesById(details.stream()
                .map(DictProcessDetail::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return details.stream()
                .map(detail -> toProcessOption(detail, categoryById.get(detail.getCategoryId())))
                .toList();
    }

    private ProcessArrangementTreeNodeDTO toNodeDTO(DictProcessArrangementNode node) {
        ProcessArrangementTreeNodeDTO dto = new ProcessArrangementTreeNodeDTO();
        dto.setTreeKey("node:" + node.getId());
        dto.setNodeType(NODE_TYPE_NODE);
        dto.setId(node.getId());
        dto.setNodeId(node.getId());
        dto.setParentId(node.getParentId());
        dto.setParentNodeId(node.getParentId());
        dto.setNodeName(node.getNodeName());
        dto.setLabel(node.getNodeName());
        dto.setSortNo(Objects.requireNonNullElse(node.getSortNo(), 0));
        return dto;
    }

    private ProcessArrangementTreeNodeDTO toItemTreeNode(
            DictProcessArrangementItem item,
            DictProcessDetail detail,
            DictExtraFeeItem fee,
            Map<Long, DictProcessCategory> categoryById) {
        ProcessArrangementTreeNodeDTO dto = toBareItemTreeNode(item);
        if (SOURCE_PROCESS.equals(item.getSourceType())) {
            DictProcessCategory category = detail == null ? null : categoryById.get(detail.getCategoryId());
            applyProcessFields(dto, detail, category);
            return dto;
        }
        applyExtraFields(dto, fee);
        return dto;
    }

    private ProcessArrangementTreeNodeDTO toBareItemTreeNode(DictProcessArrangementItem item) {
        ProcessArrangementTreeNodeDTO dto = new ProcessArrangementTreeNodeDTO();
        dto.setTreeKey("item:" + item.getId());
        dto.setNodeType(NODE_TYPE_ITEM);
        dto.setId(item.getId());
        dto.setItemId(item.getId());
        dto.setParentId(item.getParentNodeId());
        dto.setParentNodeId(item.getParentNodeId());
        dto.setSourceType(item.getSourceType());
        dto.setProcessDetailId(item.getProcessDetailId());
        dto.setFeeItemId(item.getFeeItemId());
        dto.setOptionKey(optionKey(item));
        dto.setSortNo(Objects.requireNonNullElse(item.getSortNo(), 0));
        return dto;
    }

    private ProcessArrangementOptionDTO toProcessOption(DictProcessDetail detail, DictProcessCategory category) {
        ProcessArrangementOptionDTO dto = new ProcessArrangementOptionDTO();
        dto.setOptionKey("process:" + detail.getId());
        dto.setSourceType(SOURCE_PROCESS);
        dto.setProcessDetailId(detail.getId());
        applyProcessFields(dto, detail, category);
        return dto;
    }

    private ProcessArrangementOptionDTO toExtraOption(DictExtraFeeItem item) {
        ProcessArrangementOptionDTO dto = new ProcessArrangementOptionDTO();
        dto.setOptionKey("extra:" + item.getId());
        dto.setSourceType(SOURCE_EXTRA);
        applyExtraFields(dto, item);
        return dto;
    }

    private void applyProcessFields(ProcessArrangementTreeNodeDTO dto, DictProcessDetail detail, DictProcessCategory category) {
        ProcessArrangementOptionDTO option = new ProcessArrangementOptionDTO();
        applyProcessFields(option, detail, category);
        dto.setProcessCategoryId(option.getProcessCategoryId());
        dto.setProcessCode(option.getProcessCode());
        dto.setCategoryName(option.getCategoryName());
        dto.setDetailName(option.getDetailName());
        dto.setCalcType(option.getCalcType());
        dto.setDetailHourlyRate(option.getDetailHourlyRate());
        dto.setCategoryHourlyRate(option.getCategoryHourlyRate());
        dto.setEffectiveHourlyRate(option.getEffectiveHourlyRate());
        dto.setHourlyRate(option.getHourlyRate());
        dto.setEnabled(option.getEnabled());
        dto.setDetailEnabled(option.getDetailEnabled());
        dto.setCategoryEnabled(option.getCategoryEnabled());
        dto.setEffectiveEnabled(option.getEffectiveEnabled());
        dto.setLabel(option.getLabel());
    }

    private void applyProcessFields(ProcessArrangementOptionDTO dto, DictProcessDetail detail, DictProcessCategory category) {
        BigDecimal categoryRate = category == null ? BigDecimal.ZERO : Objects.requireNonNullElse(category.getHourlyRate(), BigDecimal.ZERO);
        BigDecimal effectiveRate = detail == null || detail.getHourlyRate() == null ? categoryRate : detail.getHourlyRate();
        boolean detailEnabled = detail != null && Boolean.TRUE.equals(detail.getEnabled());
        boolean categoryEnabled = category != null && Boolean.TRUE.equals(category.getEnabled());
        dto.setProcessCategoryId(detail == null ? null : detail.getCategoryId());
        dto.setProcessCode(category == null ? null : category.getProcessCode());
        dto.setCategoryName(category == null ? null : category.getCategoryName());
        dto.setDetailName(detail == null ? null : detail.getDetailName());
        dto.setCalcType("工时");
        dto.setDetailHourlyRate(detail == null ? null : detail.getHourlyRate());
        dto.setCategoryHourlyRate(categoryRate);
        dto.setEffectiveHourlyRate(effectiveRate);
        dto.setHourlyRate(effectiveRate);
        dto.setDetailEnabled(detailEnabled);
        dto.setCategoryEnabled(categoryEnabled);
        dto.setEffectiveEnabled(detailEnabled && categoryEnabled);
        dto.setEnabled(detailEnabled && categoryEnabled);
        dto.setLabel(processLabel(detail, category, effectiveRate));
    }

    private void applyExtraFields(ProcessArrangementTreeNodeDTO dto, DictExtraFeeItem item) {
        ProcessArrangementOptionDTO option = new ProcessArrangementOptionDTO();
        applyExtraFields(option, item);
        dto.setFeeCode(option.getFeeCode());
        dto.setFeeName(option.getFeeName());
        dto.setCalcType(option.getCalcType());
        dto.setEnabled(option.getEnabled());
        dto.setLabel(option.getLabel());
    }

    private void applyExtraFields(ProcessArrangementOptionDTO dto, DictExtraFeeItem item) {
        dto.setFeeItemId(item == null ? null : item.getId());
        dto.setFeeCode(item == null ? null : item.getFeeCode());
        dto.setFeeName(item == null ? null : item.getFeeName());
        dto.setCalcType("非工时费用");
        dto.setEnabled(item != null && Boolean.TRUE.equals(item.getEnabled()));
        dto.setLabel((item == null ? "已删除费用工艺" : item.getFeeName()) + "（非工时费用）");
    }

    private String processLabel(DictProcessDetail detail, DictProcessCategory category, BigDecimal effectiveRate) {
        if (detail == null) {
            return "已删除工时工艺";
        }
        String processCode = category == null ? "-" : Objects.toString(category.getProcessCode(), "-");
        String categoryName = category == null ? "-" : Objects.toString(category.getCategoryName(), "-");
        return detail.getDetailName() + "（" + processCode + " / " + categoryName + " / "
                + effectiveRate.setScale(2, RoundingMode.HALF_UP).toPlainString() + "元/小时）";
    }

    private DictProcessArrangementNode normalizeNode(ProcessArrangementNodeSaveReq req, DictProcessArrangementNode before) {
        if (req == null) {
            throw new IllegalArgumentException("工艺编排节点请求不能为空");
        }
        DictProcessArrangementNode entity = new DictProcessArrangementNode();
        entity.setParentId(req.getParentId());
        entity.setNodeName(limit(required(req.getNodeName(), "节点名称"), "节点名称", 120));
        entity.setSortNo(req.getSortNo() == null ? before == null ? 0 : Objects.requireNonNullElse(before.getSortNo(), 0) : req.getSortNo());
        return entity;
    }

    private void requireParent(Long parentId) {
        if (parentId == null) {
            return;
        }
        if (nodeMapper.selectById(parentId) == null) {
            throw new IllegalArgumentException("父级工艺编排节点不存在");
        }
    }

    private DictProcessArrangementNode requireNode(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("工艺编排节点不能为空");
        }
        DictProcessArrangementNode node = nodeMapper.selectById(id);
        if (node == null) {
            throw new IllegalArgumentException("工艺编排节点不存在");
        }
        return node;
    }

    private DictProcessArrangementItem requireItem(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("工艺编排挂载不能为空");
        }
        DictProcessArrangementItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new IllegalArgumentException("工艺编排挂载不存在");
        }
        return item;
    }

    private void ensureNodeNameUnique(Long parentId, String nodeName, Long id) {
        QueryWrapper<DictProcessArrangementNode> query = new QueryWrapper<>();
        if (parentId == null) {
            query.isNull("parent_id");
        } else {
            query.eq("parent_id", parentId);
        }
        query.eq("node_name", nodeName);
        if (id != null) {
            query.ne("id", id);
        }
        if (nodeMapper.selectCount(query) > 0) {
            throw new IllegalArgumentException("同级工艺编排节点名称已存在");
        }
    }

    private void ensureNodeHasNoCycle(Long id, Long parentId) {
        Long cursor = parentId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null) {
            if (Objects.equals(cursor, id)) {
                throw new IllegalArgumentException("工艺编排节点不能移动到自己的下级");
            }
            if (!visited.add(cursor)) {
                throw new IllegalArgumentException("工艺编排节点存在循环父级");
            }
            DictProcessArrangementNode parent = nodeMapper.selectById(cursor);
            if (parent == null) {
                throw new IllegalArgumentException("父级工艺编排节点不存在");
            }
            cursor = parent.getParentId();
        }
    }

    private void validatePlaceableRef(ProcessArrangementItemReq ref) {
        String sourceType = normalizeRequiredSourceType(ref == null ? null : ref.getSourceType());
        if (SOURCE_PROCESS.equals(sourceType)) {
            if (ref.getProcessDetailId() == null || detailMapper.selectById(ref.getProcessDetailId()) == null) {
                throw new IllegalArgumentException("具体工艺不存在");
            }
            return;
        }
        if (ref.getFeeItemId() == null || extraFeeItemMapper.selectById(ref.getFeeItemId()) == null) {
            throw new IllegalArgumentException("非工时费用工艺不存在");
        }
    }

    private void ensureItemNotPlaced(ProcessArrangementItemReq ref) {
        String sourceType = normalizeRequiredSourceType(ref.getSourceType());
        QueryWrapper<DictProcessArrangementItem> query = new QueryWrapper<>();
        query.eq("source_type", sourceType);
        if (SOURCE_PROCESS.equals(sourceType)) {
            query.eq("process_detail_id", ref.getProcessDetailId());
        } else {
            query.eq("fee_item_id", ref.getFeeItemId());
        }
        if (itemMapper.selectCount(query) > 0) {
            throw new IllegalArgumentException("该工艺已在工艺编排中");
        }
    }

    private void ensureNoDuplicateRefs(List<ProcessArrangementItemReq> refs) {
        Set<String> keys = new HashSet<>();
        for (ProcessArrangementItemReq ref : refs) {
            String sourceType = normalizeRequiredSourceType(ref == null ? null : ref.getSourceType());
            String key = SOURCE_PROCESS.equals(sourceType)
                    ? sourceType + ":" + ref.getProcessDetailId()
                    : sourceType + ":" + ref.getFeeItemId();
            if (!keys.add(key)) {
                throw new IllegalArgumentException("同一批次不能重复选择同一个工艺");
            }
        }
    }

    private int nextItemSortNo(Long parentNodeId) {
        QueryWrapper<DictProcessArrangementItem> itemQuery = new QueryWrapper<>();
        QueryWrapper<DictProcessArrangementNode> nodeQuery = new QueryWrapper<>();
        if (parentNodeId == null) {
            itemQuery.isNull("parent_node_id");
            nodeQuery.isNull("parent_id");
        } else {
            itemQuery.eq("parent_node_id", parentNodeId);
            nodeQuery.eq("parent_id", parentNodeId);
        }
        int maxItemSortNo = rows(itemMapper.selectList(itemQuery)).stream()
                .map(DictProcessArrangementItem::getSortNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
        int maxNodeSortNo = rows(nodeMapper.selectList(nodeQuery)).stream()
                .map(DictProcessArrangementNode::getSortNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
        return Math.max(maxItemSortNo, maxNodeSortNo) + 1;
    }

    private <T> List<T> rows(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private List<DictProcessArrangementNode> descendantsOf(Long rootId, List<DictProcessArrangementNode> allNodes) {
        Map<Long, List<DictProcessArrangementNode>> childrenByParent = allNodes.stream()
                .filter(node -> node.getParentId() != null)
                .collect(Collectors.groupingBy(DictProcessArrangementNode::getParentId));
        List<DictProcessArrangementNode> result = new ArrayList<>();
        DictProcessArrangementNode root = allNodes.stream()
                .filter(node -> Objects.equals(node.getId(), rootId))
                .findFirst()
                .orElseGet(() -> requireNode(rootId));
        collectDescendants(root, childrenByParent, result);
        return result;
    }

    private void collectDescendants(
            DictProcessArrangementNode node,
            Map<Long, List<DictProcessArrangementNode>> childrenByParent,
            List<DictProcessArrangementNode> result) {
        result.add(node);
        for (DictProcessArrangementNode child : childrenByParent.getOrDefault(node.getId(), List.of())) {
            collectDescendants(child, childrenByParent, result);
        }
    }

    private Map<Long, DictProcessDetail> processDetailsById(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return toMap(detailMapper.selectBatchIds(ids), DictProcessDetail::getId);
    }

    private Map<Long, DictProcessCategory> categoriesById(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return toMap(categoryMapper.selectBatchIds(ids), DictProcessCategory::getId);
    }

    private Map<Integer, DictExtraFeeItem> extraFeesById(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return toMap(extraFeeItemMapper.selectBatchIds(ids), DictExtraFeeItem::getId);
    }

    private <T, K> Map<K, T> toMap(Collection<T> rows, Function<T, K> keyMapper) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream().filter(Objects::nonNull).collect(Collectors.toMap(keyMapper, Function.identity(), (left, right) -> left));
    }

    private void sortTree(List<ProcessArrangementTreeNodeDTO> rows) {
        rows.sort(Comparator.comparing((ProcessArrangementTreeNodeDTO row) -> Objects.requireNonNullElse(row.getSortNo(), 0))
                .thenComparing(row -> NODE_TYPE_ITEM.equals(row.getNodeType()) ? 1 : 0)
                .thenComparing(row -> Objects.requireNonNullElse(row.getId(), 0L)));
        rows.forEach(row -> sortTree(row.getChildren()));
    }

    private Map<String, Object> nodeAuditSummary(DictProcessArrangementNode node) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", node.getId());
        summary.put("parentId", node.getParentId());
        summary.put("nodeName", node.getNodeName());
        summary.put("sortNo", node.getSortNo());
        return summary;
    }

    private Map<String, Object> itemAuditSummary(DictProcessArrangementItem item) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", item.getId());
        summary.put("parentNodeId", item.getParentNodeId());
        summary.put("sourceType", item.getSourceType());
        summary.put("processDetailId", item.getProcessDetailId());
        summary.put("feeItemId", item.getFeeItemId());
        summary.put("sortNo", item.getSortNo());
        return summary;
    }

    private DictProcessArrangementItem copyItem(DictProcessArrangementItem before) {
        DictProcessArrangementItem after = new DictProcessArrangementItem();
        after.setId(before.getId());
        after.setParentNodeId(before.getParentNodeId());
        after.setSourceType(before.getSourceType());
        after.setProcessDetailId(before.getProcessDetailId());
        after.setFeeItemId(before.getFeeItemId());
        after.setSortNo(before.getSortNo());
        return after;
    }

    private String optionKey(DictProcessArrangementItem item) {
        if (SOURCE_PROCESS.equals(item.getSourceType())) {
            return "process:" + item.getProcessDetailId();
        }
        return "extra:" + item.getFeeItemId();
    }

    private String normalizeOptionalSourceType(String sourceType) {
        if (!hasText(sourceType)) {
            return null;
        }
        return normalizeRequiredSourceType(sourceType);
    }

    private String normalizeRequiredSourceType(String sourceType) {
        String normalized = sourceType == null ? "" : sourceType.trim().toLowerCase(Locale.ROOT);
        if (!SOURCE_PROCESS.equals(normalized) && !SOURCE_EXTRA.equals(normalized)) {
            throw new IllegalArgumentException("工艺类型只支持 process 或 extra");
        }
        return normalized;
    }

    private String required(String value, String label) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return trimmed;
    }

    private String limit(String value, String label, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(label + "长度不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String pattern(String keyword) {
        return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
