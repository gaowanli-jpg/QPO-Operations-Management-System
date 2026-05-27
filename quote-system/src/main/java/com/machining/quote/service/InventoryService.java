package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.InventoryModifyReq;
import com.machining.quote.dto.InventorySaveReq;
import com.machining.quote.dto.InventoryStockOpReq;
import com.machining.quote.dto.InventoryTransferReq;
import com.machining.quote.entity.InventoryMaster;
import com.machining.quote.entity.InventoryPendingStock;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.InventoryMasterMapper;
import com.machining.quote.mapper.InventoryPendingStockMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class InventoryService {
    private static final String UNKNOWN_CUSTOMER = "未指定客户";

    public static record PendingConsumption(String pendingRefNo, int qty) {
    }

    @Autowired
    private InventoryMasterMapper inventoryMapper;
    @Autowired
    private InventoryPendingStockMapper pendingMapper;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private AuditService auditService;

    public List<Map<String, Object>> list(String customerName, String keyword) {
        String customer = normalizeCustomerName(customerName);
        if (customer.isEmpty()) {
            return List.of();
        }

        QueryWrapper<InventoryMaster> qw = new QueryWrapper<>();
        qw.eq("customer_name", customer);
        if (!isBlank(keyword)) {
            String normalizedKeyword = normalizePartNo(keyword);
            qw.and(wrapper -> wrapper.like("part_no", normalizedKeyword).or().like("version", normalizeVersion(keyword)));
        }
        qw.orderByAsc("part_no").orderByAsc("version").orderByAsc("id");

        return inventoryMapper.selectList(qw).stream()
                .map(this::buildInventoryRow)
                .toList();
    }

    public List<String> listCustomers() {
        Set<String> names = new LinkedHashSet<>();

        QueryWrapper<OrderRfq> orderQw = new QueryWrapper<>();
        orderQw.select("DISTINCT customer_name");
        orderQw.isNotNull("customer_name");
        orderQw.ne("customer_name", "");
        orderQw.orderByAsc("customer_name");
        for (OrderRfq order : orderMapper.selectList(orderQw)) {
            String name = normalizeCustomerName(order.getCustomerName());
            if (!name.isEmpty() && !UNKNOWN_CUSTOMER.equals(name)) {
                names.add(name);
            }
        }

        QueryWrapper<InventoryMaster> inventoryQw = new QueryWrapper<>();
        inventoryQw.select("DISTINCT customer_name");
        inventoryQw.isNotNull("customer_name");
        inventoryQw.ne("customer_name", "");
        inventoryQw.orderByAsc("customer_name");
        for (InventoryMaster inventory : inventoryMapper.selectList(inventoryQw)) {
            String name = normalizeCustomerName(inventory.getCustomerName());
            if (!name.isEmpty() && !UNKNOWN_CUSTOMER.equals(name)) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    public List<InventoryPendingStock> listPending(Integer inventoryId) {
        if (inventoryId == null) {
            return List.of();
        }
        QueryWrapper<InventoryPendingStock> qw = new QueryWrapper<>();
        qw.eq("inventory_id", inventoryId);
        qw.gt("pending_qty", 0);
        qw.orderByAsc("id");
        return pendingMapper.selectList(qw);
    }

    @Transactional
    public InventoryMaster saveOrUpdate(InventorySaveReq req) {
        String customerName = normalizeCustomerName(req.getCustomerName());
        String partNo = normalizePartNo(req.getPartNo());
        String version = normalizeVersion(req.getVersion());
        if (customerName.isEmpty()) {
            throw new RuntimeException("请先选择客户名称");
        }
        if (partNo.isEmpty()) {
            throw new RuntimeException("图号不能为空");
        }

        InventoryMaster inventory = findByCustomerPartAndVersionForUpdate(customerName, partNo, version);
        Object before = inventory == null ? null : buildInventoryRow(inventory);
        if (inventory == null) {
            inventory = new InventoryMaster();
            inventory.setCustomerName(customerName);
            inventory.setPartNo(partNo);
            inventory.setVersion(version);
            inventory.setInStockQty(nonNegativeOrDefault(req.getInStockQty(), 0));
            inventory.setPendingQty(0);
            inventory.setPendingRefNo(null);
            inventory.setUpdatedAt(LocalDateTime.now());
            inventoryMapper.insert(inventory);
        } else if (req.getInStockQty() != null) {
            inventory.setInStockQty(nonNegative(req.getInStockQty()));
            inventory.setUpdatedAt(LocalDateTime.now());
            inventoryMapper.updateById(inventory);
        }

        if (req.getPendingQty() != null && req.getPendingQty() > 0) {
            String pendingRefNo = normalizePendingRef(req.getPendingRefNo());
            if (pendingRefNo.isEmpty()) {
                throw new RuntimeException("新增待产库存时必须填写待产单号");
            }
            addPending(inventory.getId(), pendingRefNo, req.getPendingQty());
        }
        InventoryMaster saved = inventoryMapper.selectById(inventory.getId());
        auditService.record("INVENTORY_SAVE", "inventory_master", saved.getId(), before, buildInventoryRow(saved));
        return saved;
    }

    @Transactional
    public InventoryMaster addStock(InventoryStockOpReq req) {
        InventoryMaster inventory = requireInventory(req.getInventoryId(), req.getCustomerName(), req.getPartNo(), req.getVersion());
        Object before = buildInventoryRow(inventory);
        int qty = positive(req.getQty(), "新增数量必须大于 0");
        String stockType = req.getStockType() == null ? "" : req.getStockType().trim().toUpperCase();

        if ("PENDING".equals(stockType)) {
            String pendingRefNo = normalizePendingRef(req.getPendingRefNo());
            if (pendingRefNo.isEmpty()) {
                throw new RuntimeException("待产库存必须填写待产单号");
            }
            addPending(inventory.getId(), pendingRefNo, qty);
            InventoryMaster saved = inventoryMapper.selectById(inventory.getId());
            auditService.record("INVENTORY_ADD_PENDING", "inventory_master", saved.getId(), before, buildInventoryRow(saved));
            return saved;
        }

        if (!stockType.isEmpty() && !"IN_STOCK".equals(stockType)) {
            throw new RuntimeException("未知库存类型：" + req.getStockType());
        }
        inventory.setInStockQty(safeInt(inventory.getInStockQty()) + qty);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryMapper.updateById(inventory);
        auditService.record("INVENTORY_ADD_STOCK", "inventory_master", inventory.getId(), before, buildInventoryRow(inventory));
        return inventory;
    }

    @Transactional
    public InventoryMaster transferPendingToStock(InventoryTransferReq req) {
        InventoryMaster inventory = requireInventoryById(req.getInventoryId());
        Object before = buildInventoryRow(inventory);
        String pendingRefNo = normalizePendingRef(req.getPendingRefNo());
        int qty = positive(req.getQty(), "转入数量必须大于 0");
        InventoryPendingStock pending = requirePending(inventory.getId(), pendingRefNo);
        if (qty > safeInt(pending.getPendingQty())) {
            throw new RuntimeException("转入数量不能超过该待产单号的待产库存");
        }

        int nextPendingQty = safeInt(pending.getPendingQty()) - qty;
        if (nextPendingQty == 0) {
            pendingMapper.deleteById(pending.getId());
        } else {
            pending.setPendingQty(nextPendingQty);
            pending.setUpdatedAt(LocalDateTime.now());
            pendingMapper.updateById(pending);
        }

        inventory.setInStockQty(safeInt(inventory.getInStockQty()) + qty);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryMapper.updateById(inventory);
        refreshPendingSummary(inventory.getId());
        InventoryMaster saved = inventoryMapper.selectById(inventory.getId());
        auditService.record("INVENTORY_TRANSFER_PENDING_TO_STOCK", "inventory_master", saved.getId(), before, buildInventoryRow(saved));
        return saved;
    }

    @Transactional
    public InventoryMaster modify(InventoryModifyReq req) {
        InventoryMaster inventory = requireInventoryById(req.getInventoryId());
        Object before = buildInventoryRow(inventory);
        boolean changed = false;

        if (req.getInStockQty() != null) {
            inventory.setInStockQty(nonNegative(req.getInStockQty()));
            changed = true;
        }

        if (req.getPendingQty() != null || !isBlank(req.getPendingRefNo())) {
            String pendingRefNo = normalizePendingRef(req.getPendingRefNo());
            if (pendingRefNo.isEmpty()) {
                throw new RuntimeException("修改待产库存时必须选择待产单号");
            }
            int pendingQty = nonNegativeOrDefault(req.getPendingQty(), 0);
            InventoryPendingStock pending = findPendingForUpdate(inventory.getId(), pendingRefNo);
            if (pendingQty == 0) {
                if (pending != null) {
                    pendingMapper.deleteById(pending.getId());
                }
            } else if (pending == null) {
                pending = new InventoryPendingStock();
                pending.setInventoryId(inventory.getId());
                pending.setPendingRefNo(pendingRefNo);
                pending.setPendingQty(pendingQty);
                pending.setUpdatedAt(LocalDateTime.now());
                pendingMapper.insert(pending);
            } else {
                pending.setPendingQty(pendingQty);
                pending.setUpdatedAt(LocalDateTime.now());
                pendingMapper.updateById(pending);
            }
            changed = true;
        }

        if (changed) {
            inventory.setUpdatedAt(LocalDateTime.now());
            inventoryMapper.updateById(inventory);
            refreshPendingSummary(inventory.getId());
        }
        InventoryMaster saved = inventoryMapper.selectById(inventory.getId());
        if (changed) {
            auditService.record("INVENTORY_MODIFY", "inventory_master", saved.getId(), before, buildInventoryRow(saved));
        }
        return saved;
    }

    public InventoryMaster findByCustomerAndPartNo(String customerName, String partNo) {
        return findByCustomerPartAndVersion(customerName, partNo, null);
    }

    public InventoryMaster findByCustomerPartAndVersion(String customerName, String partNo, String version) {
        String customer = normalizeCustomerName(customerName);
        String normalizedPartNo = normalizePartNo(partNo);
        String normalizedVersion = normalizeVersion(version);
        if (customer.isEmpty() || normalizedPartNo.isEmpty()) {
            return null;
        }
        QueryWrapper<InventoryMaster> qw = new QueryWrapper<>();
        qw.eq("customer_name", customer);
        qw.eq("part_no", normalizedPartNo);
        qw.eq("version", normalizedVersion);
        qw.last("LIMIT 1");
        return inventoryMapper.selectOne(qw);
    }

    public InventoryMaster findByCustomerAndPartNoForUpdate(String customerName, String partNo) {
        return findByCustomerPartAndVersionForUpdate(customerName, partNo, null);
    }

    public InventoryMaster findByCustomerPartAndVersionForUpdate(String customerName, String partNo, String version) {
        String customer = normalizeCustomerName(customerName);
        String normalizedPartNo = normalizePartNo(partNo);
        String normalizedVersion = normalizeVersion(version);
        if (customer.isEmpty() || normalizedPartNo.isEmpty()) {
            return null;
        }
        return inventoryMapper.selectByCustomerPartAndVersionForUpdate(customer, normalizedPartNo, normalizedVersion);
    }

    @Transactional
    public List<PendingConsumption> consumePending(Integer inventoryId, int requestedQty) {
        if (requestedQty <= 0) {
            return List.of();
        }
        requireInventoryById(inventoryId);

        int remaining = requestedQty;
        List<PendingConsumption> consumptions = new ArrayList<>();
        for (InventoryPendingStock pending : pendingMapper.listByInventoryForUpdate(inventoryId)) {
            if (remaining <= 0) {
                break;
            }
            int used = Math.min(safeInt(pending.getPendingQty()), remaining);
            if (used <= 0) {
                continue;
            }
            int nextQty = safeInt(pending.getPendingQty()) - used;
            if (nextQty == 0) {
                pendingMapper.deleteById(pending.getId());
            } else {
                pending.setPendingQty(nextQty);
                pending.setUpdatedAt(LocalDateTime.now());
                pendingMapper.updateById(pending);
            }
            consumptions.add(new PendingConsumption(pending.getPendingRefNo(), used));
            remaining -= used;
        }
        if (!consumptions.isEmpty()) {
            refreshPendingSummary(inventoryId);
        }
        return consumptions;
    }

    @Transactional
    public int consumeInStock(Integer inventoryId, int requestedQty) {
        if (requestedQty <= 0) {
            return 0;
        }
        InventoryMaster inventory = requireInventoryById(inventoryId);
        int used = Math.min(safeInt(inventory.getInStockQty()), requestedQty);
        if (used <= 0) {
            return 0;
        }
        inventory.setInStockQty(safeInt(inventory.getInStockQty()) - used);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryMapper.updateById(inventory);
        return used;
    }

    @Transactional
    public void restorePending(String customerName, String partNo, String pendingRefNo, int qty) {
        restorePending(customerName, partNo, null, pendingRefNo, qty);
    }

    @Transactional
    public void restorePending(String customerName, String partNo, String version, String pendingRefNo, int qty) {
        if (qty <= 0) {
            return;
        }
        InventoryMaster inventory = findOrCreate(customerName, partNo, version);
        Object before = buildInventoryRow(inventory);
        String safeRefNo = normalizePendingRef(pendingRefNo);
        addPending(inventory.getId(), safeRefNo.isEmpty() ? "冲销待产" : safeRefNo, qty);
        InventoryMaster saved = inventoryMapper.selectById(inventory.getId());
        auditService.record("INVENTORY_RESTORE_PENDING", "inventory_master", saved.getId(), before, buildInventoryRow(saved));
    }

    @Transactional
    public void restoreInStock(String customerName, String partNo, int qty) {
        restoreInStock(customerName, partNo, null, qty);
    }

    @Transactional
    public void restoreInStock(String customerName, String partNo, String version, int qty) {
        if (qty <= 0) {
            return;
        }
        InventoryMaster inventory = findOrCreate(customerName, partNo, version);
        Object before = buildInventoryRow(inventory);
        inventory.setInStockQty(safeInt(inventory.getInStockQty()) + qty);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryMapper.updateById(inventory);
        auditService.record("INVENTORY_RESTORE_STOCK", "inventory_master", inventory.getId(), before, buildInventoryRow(inventory));
    }

    public String normalizePartNo(String partNo) {
        return partNo == null ? "" : partNo.trim().toUpperCase();
    }

    public String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim().toUpperCase();
        return normalized.isEmpty() ? "V1" : normalized;
    }

    public String normalizeCustomerName(String customerName) {
        return customerName == null ? "" : customerName.trim();
    }

    private InventoryMaster findOrCreate(String customerName, String partNo, String version) {
        String customer = normalizeCustomerName(customerName);
        String normalizedPartNo = normalizePartNo(partNo);
        String normalizedVersion = normalizeVersion(version);
        if (customer.isEmpty()) {
            customer = UNKNOWN_CUSTOMER;
        }
        if (normalizedPartNo.isEmpty()) {
            throw new RuntimeException("图号不能为空");
        }
        InventoryMaster inventory = findByCustomerPartAndVersionForUpdate(customer, normalizedPartNo, normalizedVersion);
        if (inventory != null) {
            return inventory;
        }
        inventory = new InventoryMaster();
        inventory.setCustomerName(customer);
        inventory.setPartNo(normalizedPartNo);
        inventory.setVersion(normalizedVersion);
        inventory.setInStockQty(0);
        inventory.setPendingQty(0);
        inventory.setPendingRefNo(null);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryMapper.insert(inventory);
        return inventory;
    }

    private InventoryMaster requireInventory(Integer inventoryId, String customerName, String partNo, String version) {
        if (inventoryId != null) {
            return requireInventoryById(inventoryId);
        }
        InventoryMaster inventory = findByCustomerPartAndVersionForUpdate(customerName, partNo, version);
        if (inventory == null) {
            throw new RuntimeException("请先录入该客户的图号和版本");
        }
        return inventory;
    }

    private InventoryMaster requireInventoryById(Integer inventoryId) {
        if (inventoryId == null) {
            throw new RuntimeException("库存记录 ID 不能为空");
        }
        InventoryMaster inventory = inventoryMapper.selectByIdForUpdate(inventoryId);
        if (inventory == null) {
            throw new RuntimeException("库存记录不存在");
        }
        return inventory;
    }

    private Map<String, Object> buildInventoryRow(InventoryMaster inventory) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", inventory.getId());
        row.put("customerName", inventory.getCustomerName());
        row.put("partNo", inventory.getPartNo());
        row.put("version", inventory.getVersion());
        row.put("inStockQty", safeInt(inventory.getInStockQty()));
        row.put("pendingQty", safeInt(inventory.getPendingQty()));
        row.put("pendingRefNo", inventory.getPendingRefNo());
        row.put("pendingRows", listPending(inventory.getId()));
        row.put("updatedAt", inventory.getUpdatedAt());
        return row;
    }

    private void addPending(Integer inventoryId, String pendingRefNo, int qty) {
        int safeQty = positive(qty, "待产库存数量必须大于 0");
        InventoryPendingStock pending = findPendingForUpdate(inventoryId, pendingRefNo);
        if (pending == null) {
            pending = new InventoryPendingStock();
            pending.setInventoryId(inventoryId);
            pending.setPendingRefNo(pendingRefNo);
            pending.setPendingQty(safeQty);
            pending.setUpdatedAt(LocalDateTime.now());
            pendingMapper.insert(pending);
        } else {
            pending.setPendingQty(safeInt(pending.getPendingQty()) + safeQty);
            pending.setUpdatedAt(LocalDateTime.now());
            pendingMapper.updateById(pending);
        }
        refreshPendingSummary(inventoryId);
    }

    private void refreshPendingSummary(Integer inventoryId) {
        InventoryMaster inventory = inventoryMapper.selectById(inventoryId);
        if (inventory == null) {
            return;
        }
        List<InventoryPendingStock> rows = listPending(inventoryId);
        int total = rows.stream().mapToInt(row -> safeInt(row.getPendingQty())).sum();
        inventory.setPendingQty(total);
        if (rows.isEmpty()) {
            inventory.setPendingRefNo(null);
        } else if (rows.size() == 1) {
            inventory.setPendingRefNo(rows.get(0).getPendingRefNo());
        } else {
            inventory.setPendingRefNo(rows.size() + "个待产单");
        }
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryMapper.updateById(inventory);
    }

    private InventoryPendingStock requirePending(Integer inventoryId, String pendingRefNo) {
        if (isBlank(pendingRefNo)) {
            throw new RuntimeException("请先选择待产单号");
        }
        InventoryPendingStock pending = findPendingForUpdate(inventoryId, pendingRefNo);
        if (pending == null || safeInt(pending.getPendingQty()) <= 0) {
            throw new RuntimeException("该待产单号没有可转入库存");
        }
        return pending;
    }

    private InventoryPendingStock findPending(Integer inventoryId, String pendingRefNo) {
        if (inventoryId == null || isBlank(pendingRefNo)) {
            return null;
        }
        QueryWrapper<InventoryPendingStock> qw = new QueryWrapper<>();
        qw.eq("inventory_id", inventoryId);
        qw.eq("pending_ref_no", normalizePendingRef(pendingRefNo));
        qw.last("LIMIT 1");
        return pendingMapper.selectOne(qw);
    }

    private InventoryPendingStock findPendingForUpdate(Integer inventoryId, String pendingRefNo) {
        if (inventoryId == null || isBlank(pendingRefNo)) {
            return null;
        }
        return pendingMapper.selectByInventoryAndRefForUpdate(inventoryId, normalizePendingRef(pendingRefNo));
    }

    private String normalizePendingRef(String pendingRefNo) {
        return pendingRefNo == null ? "" : pendingRefNo.trim();
    }

    private int nonNegative(Integer value) {
        return nonNegativeOrDefault(value, 0);
    }

    private int nonNegativeOrDefault(Integer value, int defaultValue) {
        int safeValue = value == null ? defaultValue : value;
        if (safeValue < 0) {
            throw new RuntimeException("库存数量不能为负数");
        }
        return safeValue;
    }

    private int positive(Integer value, String message) {
        int safeValue = safeInt(value);
        if (safeValue <= 0) {
            throw new RuntimeException(message);
        }
        return safeValue;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
