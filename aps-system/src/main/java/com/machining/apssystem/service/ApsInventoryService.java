package com.machining.apssystem.service;

import com.machining.apssystem.dto.ApsMaterialInventoryDTO;
import com.machining.apssystem.dto.ApsMaterialLockDTO;
import com.machining.apssystem.dto.ApsMaterialShortageDTO;
import com.machining.apssystem.dto.ApsPartInventoryDTO;
import com.machining.apssystem.dto.ApsPartInventoryLockDTO;
import com.machining.apssystem.dto.ApsPartInventoryPageDTO;
import com.machining.apssystem.dto.ApsPartInventoryPendingStockDTO;
import com.machining.apssystem.dto.ApsPartProductionRequirementDTO;
import com.machining.apssystem.dto.MaterialInventorySaveReq;
import com.machining.apssystem.dto.PartInventoryModifyReq;
import com.machining.apssystem.dto.PartInventorySaveReq;
import com.machining.apssystem.dto.PartInventoryStockOpReq;
import com.machining.apssystem.dto.PartInventoryTransferReq;
import com.machining.apssystem.entity.ApsMaterialInventory;
import com.machining.apssystem.entity.ApsPartInventory;
import com.machining.apssystem.entity.ApsPartInventoryPendingStock;
import com.machining.apssystem.mapper.ApsMaterialInventoryMapper;
import com.machining.apssystem.mapper.ApsMaterialLockMapper;
import com.machining.apssystem.mapper.ApsMaterialShortageMapper;
import com.machining.apssystem.mapper.ApsPartInventoryLockMapper;
import com.machining.apssystem.mapper.ApsPartInventoryMapper;
import com.machining.apssystem.mapper.ApsPartInventoryPendingStockMapper;
import com.machining.apssystem.mapper.ApsProductionRequirementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ApsInventoryService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    @Autowired
    private ApsPartInventoryMapper partInventoryMapper;

    @Autowired
    private ApsPartInventoryPendingStockMapper partInventoryPendingStockMapper;

    @Autowired
    private ApsPartInventoryLockMapper partInventoryLockMapper;

    @Autowired
    private ApsProductionRequirementMapper productionRequirementMapper;

    @Autowired
    private ApsMaterialInventoryMapper materialInventoryMapper;

    @Autowired
    private ApsMaterialLockMapper materialLockMapper;

    @Autowired
    private ApsMaterialShortageMapper materialShortageMapper;

    public List<String> listPartInventoryCustomers() {
        return partInventoryMapper.selectCustomerNames().stream()
                .map(this::text)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    public ApsPartInventoryPageDTO listPartInventoryPage(
            String customerName,
            String keyword,
            Integer page,
            Integer pageSize) {
        String customer = text(customerName);
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);

        ApsPartInventoryPageDTO result = new ApsPartInventoryPageDTO();
        result.setPage(safePage);
        result.setPageSize(safePageSize);
        if (customer.isEmpty()) {
            result.setRecords(List.of());
            result.setTotal(0);
            return result;
        }

        String like = likeKeyword(keyword);
        long total = partInventoryMapper.countPage(customer, like);
        List<ApsPartInventoryDTO> records = partInventoryMapper.selectPageDtos(
                customer, like, safePageSize, (safePage - 1) * safePageSize);
        records.forEach(this::attachPendingRows);

        result.setRecords(records);
        result.setTotal(total);
        return result;
    }

    public List<ApsPartInventoryDTO> listPartInventory(String keyword, Integer requestedLimit) {
        return partInventoryMapper.selectListDtos(likeKeyword(keyword), normalizeLimit(requestedLimit));
    }

    @Transactional
    public ApsPartInventoryDTO savePartInventory(PartInventorySaveReq req) {
        ApsPartInventory inventory = new ApsPartInventory();
        inventory.setCustomerName(requireText(req.getCustomerName(), "customerName"));
        inventory.setPartNo(normalizePartNoRequired(req.getPartNo()));
        inventory.setVersion(normalizeVersion(req.getVersion()));
        inventory.setInStockQty(nonNegative(req.getInStockQty()));
        inventory.setInProductionQty(nonNegative(req.getInProductionQty()));
        inventory.setRemark(text(req.getRemark()));

        Long id = partInventoryMapper.insertReturningIdIfAbsent(inventory);
        if (id == null) {
            throw new IllegalStateException("Part inventory already exists. Use the update endpoint to change quantities.");
        }
        return findPartInventory(id);
    }

    @Transactional
    public ApsPartInventoryDTO addPartStock(PartInventoryStockOpReq req) {
        ApsPartInventory inventory = requirePartInventory(
                req.getInventoryId(), req.getCustomerName(), req.getPartNo(), req.getVersion());
        Long inventoryId = inventory.getId();
        int qty = positive(req.getQty(), "Quantity must be greater than 0.");
        String stockType = text(req.getStockType()).toUpperCase();
        if ("PENDING".equals(stockType) || "IN_PRODUCTION".equals(stockType)) {
            addPendingStock(inventoryId, requireText(req.getPendingRefNo(), "pendingRefNo"), qty);
            partInventoryMapper.refreshPendingSummary(inventoryId);
            return findPartInventory(inventoryId);
        }
        if (!stockType.isEmpty() && !"IN_STOCK".equals(stockType)) {
            throw new IllegalArgumentException("Unknown stockType: " + req.getStockType());
        }
        partInventoryMapper.addInStockQty(inventoryId, qty);
        return findPartInventory(inventoryId);
    }

    @Transactional
    public ApsPartInventoryDTO transferPartPendingToStock(PartInventoryTransferReq req) {
        ApsPartInventory inventory = requirePartInventory(req.getInventoryId(), null, null, null);
        Long inventoryId = inventory.getId();
        String pendingRefNo = requireText(req.getPendingRefNo(), "pendingRefNo");
        int qty = positive(req.getQty(), "Quantity must be greater than 0.");
        ApsPartInventoryPendingStock pending = requirePendingStock(inventoryId, pendingRefNo);
        int availableQty = numberInt(pending.getAvailableQty());
        if (qty > availableQty) {
            throw new IllegalStateException("Transfer quantity cannot exceed available pending stock.");
        }

        int nextPendingQty = numberInt(pending.getPendingQty()) - qty;
        if (nextPendingQty == 0) {
            partInventoryPendingStockMapper.deleteById(pending.getId());
        } else {
            partInventoryPendingStockMapper.setPendingQty(pending.getId(), nextPendingQty);
        }
        partInventoryMapper.addInStockQty(inventoryId, qty);
        partInventoryMapper.refreshPendingSummary(inventoryId);
        return findPartInventory(inventoryId);
    }

    @Transactional
    public ApsPartInventoryDTO modifyPartInventory(PartInventoryModifyReq req) {
        ApsPartInventory inventory = requirePartInventory(req.getInventoryId(), null, null, null);
        Long inventoryId = inventory.getId();
        if (req.getInStockQty() != null) {
            int inStockQty = nonNegative(req.getInStockQty());
            if (inStockQty < numberInt(inventory.getLockedInStockQty())) {
                throw new IllegalStateException("In-stock quantity cannot be lower than locked in-stock quantity.");
            }
            partInventoryMapper.setInStockQty(inventoryId, inStockQty);
        }

        if (req.getPendingQty() != null || !text(req.getPendingRefNo()).isEmpty()) {
            String pendingRefNo = requireText(req.getPendingRefNo(), "pendingRefNo");
            int pendingQty = nonNegative(req.getPendingQty());
            ApsPartInventoryPendingStock pending =
                    partInventoryPendingStockMapper.selectByInventoryAndRefForUpdate(inventoryId, pendingRefNo);
            int lockedQty = pending == null ? 0 : numberInt(pending.getLockedQty());
            if (pendingQty < lockedQty) {
                throw new IllegalStateException("Pending quantity cannot be lower than locked pending quantity.");
            }
            if (pendingQty == 0) {
                if (pending != null) {
                    partInventoryPendingStockMapper.deleteById(pending.getId());
                }
            } else if (pending == null) {
                ApsPartInventoryPendingStock row = new ApsPartInventoryPendingStock();
                row.setPartInventoryId(inventoryId);
                row.setPendingRefNo(pendingRefNo);
                row.setPendingQty(pendingQty);
                row.setLockedQty(0);
                partInventoryPendingStockMapper.insert(row);
            } else {
                partInventoryPendingStockMapper.setPendingQty(pending.getId(), pendingQty);
            }
            partInventoryMapper.refreshPendingSummary(inventoryId);
        }
        return findPartInventory(inventoryId);
    }

    @Transactional
    public ApsPartInventoryDTO updatePartInventory(Long id, PartInventorySaveReq req) {
        requireId(id, "part inventory id");
        ApsPartInventory current = findPartInventoryForUpdate(id);
        int inStockQty = nonNegative(req.getInStockQty());
        int inProductionQty = nonNegative(req.getInProductionQty());
        if (inStockQty < numberInt(current.getLockedInStockQty())) {
            throw new IllegalStateException("In-stock quantity cannot be lower than locked in-stock quantity.");
        }
        if (inProductionQty < numberInt(current.getLockedInProductionQty())) {
            throw new IllegalStateException("In-production quantity cannot be lower than locked in-production quantity.");
        }

        int updated = partInventoryMapper.updateDetails(
                id,
                requireText(req.getCustomerName(), "customerName"),
                normalizePartNoRequired(req.getPartNo()),
                normalizeVersion(req.getVersion()),
                inStockQty,
                inProductionQty,
                text(req.getRemark()));
        if (updated == 0) {
            throw new IllegalArgumentException("Part inventory not found.");
        }
        return findPartInventory(id);
    }

    @Transactional
    public void deletePartInventory(Long id) {
        ApsPartInventory inventory = findPartInventoryForUpdate(id);
        int locked = numberInt(inventory.getLockedInStockQty()) + numberInt(inventory.getLockedInProductionQty());
        if (locked > 0) {
            throw new IllegalStateException("Part inventory has locked quantity and cannot be deleted.");
        }
        partInventoryMapper.deleteById(id);
    }

    public List<ApsPartInventoryPendingStockDTO> listPartPendingStocks(Long inventoryId) {
        if (inventoryId == null || inventoryId <= 0) {
            return List.of();
        }
        return partInventoryPendingStockMapper.selectDtosByInventoryId(inventoryId);
    }

    public List<ApsPartInventoryLockDTO> listPartLocks(Integer requestedLimit) {
        return partInventoryLockMapper.selectDtos(normalizeLimit(requestedLimit));
    }

    public List<ApsPartProductionRequirementDTO> listProductionRequirements(String status, Integer requestedLimit) {
        return productionRequirementMapper.selectDtos(text(status), normalizeLimit(requestedLimit));
    }

    public List<ApsMaterialInventoryDTO> listMaterialInventory(String keyword, Integer requestedLimit) {
        return materialInventoryMapper.selectDtos(likeKeyword(keyword), normalizeLimit(requestedLimit));
    }

    @Transactional
    public ApsMaterialInventoryDTO saveMaterialInventory(MaterialInventorySaveReq req) {
        ApsMaterialInventory inventory = new ApsMaterialInventory();
        inventory.setMaterialName(requireText(req.getMaterialName(), "materialName"));
        inventory.setMaterialType(text(req.getMaterialType()));
        inventory.setStockWeightKg(nonNegative(req.getStockWeightKg()));
        inventory.setSafetyStockWeightKg(nonNegative(req.getSafetyStockWeightKg()));
        inventory.setUnit(defaultText(req.getUnit(), "kg"));
        inventory.setRemark(text(req.getRemark()));

        Long id = materialInventoryMapper.insertReturningIdIfAbsent(inventory);
        if (id == null) {
            throw new IllegalStateException("Material inventory already exists. Use the update endpoint to change weights.");
        }
        return findMaterialInventory(id);
    }

    @Transactional
    public ApsMaterialInventoryDTO updateMaterialInventory(Long id, MaterialInventorySaveReq req) {
        requireId(id, "material inventory id");
        ApsMaterialInventory current = findMaterialInventoryForUpdate(id);

        BigDecimal stockWeightKg = nonNegative(req.getStockWeightKg());
        if (stockWeightKg.compareTo(decimal(current.getLockedWeightKg())) < 0) {
            throw new IllegalStateException("Stock weight cannot be lower than locked weight.");
        }
        int updated = materialInventoryMapper.updateDetails(
                id,
                requireText(req.getMaterialName(), "materialName"),
                text(req.getMaterialType()),
                stockWeightKg,
                nonNegative(req.getSafetyStockWeightKg()),
                defaultText(req.getUnit(), "kg"),
                text(req.getRemark()));
        if (updated == 0) {
            throw new IllegalArgumentException("Material inventory not found.");
        }
        return findMaterialInventory(id);
    }

    @Transactional
    public void deleteMaterialInventory(Long id) {
        ApsMaterialInventory inventory = findMaterialInventoryForUpdate(id);
        if (decimal(inventory.getLockedWeightKg()).compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Material inventory has locked weight and cannot be deleted.");
        }
        materialInventoryMapper.deleteById(id);
    }

    public List<ApsMaterialLockDTO> listMaterialLocks(Integer requestedLimit) {
        return materialLockMapper.selectDtos(normalizeLimit(requestedLimit));
    }

    public List<ApsMaterialShortageDTO> listMaterialShortages(String status, Integer requestedLimit) {
        return materialShortageMapper.selectDtos(text(status), normalizeLimit(requestedLimit));
    }

    private ApsPartInventoryDTO findPartInventory(Long id) {
        requireId(id, "part inventory id");
        ApsPartInventory inventory = partInventoryMapper.selectById(id);
        if (inventory == null) {
            throw new IllegalArgumentException("Part inventory not found.");
        }
        ApsPartInventoryDTO dto = ApsPartInventoryDTO.from(inventory);
        attachPendingRows(dto);
        return dto;
    }

    private ApsPartInventory findPartInventoryForUpdate(Long id) {
        requireId(id, "part inventory id");
        ApsPartInventory inventory = partInventoryMapper.selectByIdForUpdate(id);
        if (inventory == null) {
            throw new IllegalArgumentException("Part inventory not found.");
        }
        return inventory;
    }

    private ApsMaterialInventoryDTO findMaterialInventory(Long id) {
        requireId(id, "material inventory id");
        ApsMaterialInventory inventory = materialInventoryMapper.selectById(id);
        if (inventory == null) {
            throw new IllegalArgumentException("Material inventory not found.");
        }
        return ApsMaterialInventoryDTO.from(inventory);
    }

    private ApsMaterialInventory findMaterialInventoryForUpdate(Long id) {
        requireId(id, "material inventory id");
        ApsMaterialInventory inventory = materialInventoryMapper.selectByIdForUpdate(id);
        if (inventory == null) {
            throw new IllegalArgumentException("Material inventory not found.");
        }
        return inventory;
    }

    private void attachPendingRows(ApsPartInventoryDTO row) {
        if (row != null && row.getId() != null) {
            row.setPendingRows(listPartPendingStocks(row.getId()));
        }
    }

    private ApsPartInventory requirePartInventory(Long inventoryId, String customerName, String partNo, String version) {
        if (inventoryId != null && inventoryId > 0) {
            return findPartInventoryForUpdate(inventoryId);
        }
        String customer = requireText(customerName, "customerName");
        String normalizedPartNo = normalizePartNoRequired(partNo);
        String normalizedVersion = normalizeVersion(version);
        ApsPartInventory row = partInventoryMapper.selectByCustomerPartVersionForUpdate(
                customer, normalizedPartNo, normalizedVersion);
        if (row == null) {
            throw new IllegalArgumentException("Please create the customer part/version inventory first.");
        }
        return row;
    }

    private void addPendingStock(Long inventoryId, String pendingRefNo, int qty) {
        ApsPartInventoryPendingStock pending =
                partInventoryPendingStockMapper.selectByInventoryAndRefForUpdate(inventoryId, pendingRefNo);
        if (pending == null) {
            ApsPartInventoryPendingStock row = new ApsPartInventoryPendingStock();
            row.setPartInventoryId(inventoryId);
            row.setPendingRefNo(pendingRefNo);
            row.setPendingQty(qty);
            row.setLockedQty(0);
            partInventoryPendingStockMapper.insert(row);
            return;
        }
        partInventoryPendingStockMapper.addPendingQty(pending.getId(), qty);
    }

    private ApsPartInventoryPendingStock requirePendingStock(Long inventoryId, String pendingRefNo) {
        ApsPartInventoryPendingStock pending =
                partInventoryPendingStockMapper.selectByInventoryAndRefForUpdate(inventoryId, pendingRefNo);
        if (pending == null || numberInt(pending.getAvailableQty()) <= 0) {
            throw new IllegalStateException("Selected pending reference has no available stock.");
        }
        return pending;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private String likeKeyword(String value) {
        String keyword = text(value);
        return keyword.isEmpty() ? "" : "%" + keyword.toUpperCase() + "%";
    }

    private void requireId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }

    private String requireText(String value, String fieldName) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return text;
    }

    private String normalizePartNoRequired(String value) {
        String normalized = normalizePartNo(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("partNo is required.");
        }
        return normalized;
    }

    private String normalizePartNo(String value) {
        return text(value).toUpperCase();
    }

    private String normalizeVersion(String value) {
        return text(value).toUpperCase();
    }

    private String defaultText(String value, String fallback) {
        String text = text(value);
        return text.isEmpty() ? fallback : text;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private int nonNegative(Integer value) {
        int number = value == null ? 0 : value;
        if (number < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }
        return number;
    }

    private int positive(Integer value, String message) {
        int number = value == null ? 0 : value;
        if (number <= 0) {
            throw new IllegalArgumentException(message);
        }
        return number;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        BigDecimal number = value == null ? BigDecimal.ZERO : value;
        if (number.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Weight cannot be negative.");
        }
        return number;
    }

    private int numberInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
