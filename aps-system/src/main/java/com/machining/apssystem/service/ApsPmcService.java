package com.machining.apssystem.service;

import com.machining.apssystem.dto.DepartmentAssignmentReq;
import com.machining.apssystem.dto.WorkItemBulkAssignReq;
import com.machining.apssystem.dto.WorkItemBulkDeleteReq;
import com.machining.apssystem.dto.ApsPmcTaskDetailDTO;
import com.machining.apssystem.dto.ApsWorkItemPageDTO;
import com.machining.apssystem.dto.ApsWorkItemDTO;
import com.machining.apssystem.entity.ApsDepartment;
import com.machining.apssystem.entity.ApsDepartmentAssignment;
import com.machining.apssystem.entity.ApsDepartmentTask;
import com.machining.apssystem.entity.ApsMaterialLock;
import com.machining.apssystem.entity.ApsPartInventoryLock;
import com.machining.apssystem.entity.ApsPartInventoryLockDetail;
import com.machining.apssystem.entity.ApsPartInventoryPendingStock;
import com.machining.apssystem.mapper.ApsDepartmentAssignmentMapper;
import com.machining.apssystem.mapper.ApsDepartmentMapper;
import com.machining.apssystem.mapper.ApsDepartmentTaskMapper;
import com.machining.apssystem.mapper.ApsMaterialInventoryMapper;
import com.machining.apssystem.mapper.ApsMaterialLockMapper;
import com.machining.apssystem.mapper.ApsMaterialShortageMapper;
import com.machining.apssystem.mapper.ApsPartInventoryLockDetailMapper;
import com.machining.apssystem.mapper.ApsPartInventoryLockMapper;
import com.machining.apssystem.mapper.ApsPartInventoryMapper;
import com.machining.apssystem.mapper.ApsPartInventoryPendingStockMapper;
import com.machining.apssystem.mapper.ApsWorkItemMapper;
import com.machining.apssystem.mapper.ApsWorkItemProcessMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ApsPmcService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    @Autowired
    private ApsDepartmentMapper departmentMapper;

    @Autowired
    private ApsDepartmentAssignmentMapper assignmentMapper;

    @Autowired
    private ApsDepartmentTaskMapper departmentTaskMapper;

    @Autowired
    private ApsPartInventoryMapper partInventoryMapper;

    @Autowired
    private ApsPartInventoryLockMapper partInventoryLockMapper;

    @Autowired
    private ApsPartInventoryLockDetailMapper partInventoryLockDetailMapper;

    @Autowired
    private ApsPartInventoryPendingStockMapper partInventoryPendingStockMapper;

    @Autowired
    private ApsMaterialInventoryMapper materialInventoryMapper;

    @Autowired
    private ApsMaterialLockMapper materialLockMapper;

    @Autowired
    private ApsMaterialShortageMapper materialShortageMapper;

    @Autowired
    private ApsWorkItemMapper workItemMapper;

    @Autowired
    private ApsWorkItemProcessMapper workItemProcessMapper;

    public List<ApsWorkItemDTO> listPmcTasks(String assignmentStatus, Integer requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        String safeStatus = text(assignmentStatus).toUpperCase();
        return workItemMapper.selectPmcTaskDtos(safeStatus, limit);
    }

    public ApsWorkItemPageDTO listPmcTaskPage(String assignmentStatus, Integer page, Integer pageSize) {
        return listPmcTaskPage(assignmentStatus, null, null, page, pageSize);
    }

    public ApsWorkItemPageDTO listPmcTaskPage(
            String assignmentStatus,
            String customerName,
            String sortMode,
            Integer page,
            Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        String safeStatus = text(assignmentStatus).toUpperCase();
        String safeCustomerName = text(customerName);
        String safeSortMode = normalizeSortMode(sortMode);
        ApsWorkItemPageDTO result = new ApsWorkItemPageDTO();
        result.setPage(safePage);
        result.setPageSize(safePageSize);
        result.setTotal(workItemMapper.countPmcTaskPage(safeStatus, safeCustomerName));
        result.setRecords(workItemMapper.selectPmcTaskPageDtos(
                safeStatus, safeCustomerName, safeSortMode, safePageSize, (safePage - 1) * safePageSize));
        return result;
    }

    public List<String> listTaskCustomerNames(String assignmentStatus) {
        return workItemMapper.selectTaskCustomerNames(text(assignmentStatus).toUpperCase());
    }

    public ApsPmcTaskDetailDTO getTaskDetail(Long workItemId) {
        ApsPmcTaskDetailDTO result = new ApsPmcTaskDetailDTO();
        result.setTask(findWorkItem(workItemId, false));
        result.setProcessRows(workItemProcessMapper.selectDtosByWorkItemId(workItemId));
        result.setPartLock(partInventoryLockMapper.selectDtoByWorkItemId(workItemId));
        result.setMaterialLock(materialLockMapper.selectDtoByWorkItemId(workItemId));
        result.setMaterialShortages(materialShortageMapper.selectDtosByWorkItemId(workItemId));
        result.setAssignments(assignmentMapper.selectDtosByWorkItemId(workItemId));
        return result;
    }

    @Transactional
    public Map<String, Object> deleteWorkItems(WorkItemBulkDeleteReq req) {
        Set<Long> ids = normalizeIds(req == null ? null : req.getIds());
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Select at least one APS task to delete.");
        }

        List<Long> deletedIds = new ArrayList<>();
        for (Long workItemId : ids) {
            findWorkItem(workItemId, true);
            releaseActiveAssignment(workItemId);
            releasePartLocks(workItemId);
            releaseMaterialLocks(workItemId);
            materialShortageMapper.deleteByWorkItemId(workItemId);
            workItemMapper.deleteById(workItemId);
            deletedIds.add(workItemId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("deletedCount", deletedIds.size());
        result.put("deletedIds", deletedIds);
        return result;
    }

    @Transactional
    public ApsPmcTaskDetailDTO assignDepartment(Long workItemId, DepartmentAssignmentReq req) {
        findWorkItem(workItemId, true);
        Long departmentId = requireIdValue(req == null ? null : req.getDepartmentId(), "departmentId");
        ApsDepartment department = findDepartmentForUpdate(departmentId);
        if (!"ENABLED".equalsIgnoreCase(department.getDepartmentStatus())) {
            throw new IllegalStateException("Department is disabled.");
        }

        releaseActiveAssignment(workItemId);
        BigDecimal requiredHours = requiredHours(workItemId);
        department = findDepartmentForUpdate(departmentId);
        BigDecimal remainingHours = decimal(department.getRemainingHours());
        if (requiredHours.compareTo(remainingHours) > 0) {
            throw new IllegalStateException("Department remaining hours are not enough.");
        }
        departmentMapper.addOccupiedHours(departmentId, requiredHours);

        ApsDepartmentAssignment assignment = new ApsDepartmentAssignment();
        assignment.setWorkItemId(workItemId);
        assignment.setDepartmentId(departmentId);
        assignment.setDepartmentName(department.getDepartmentName());
        assignment.setRequiredHours(requiredHours);
        assignment.setStatus("ASSIGNED");
        assignment.setAssignedBy(text(req.getAssignedBy()));
        assignment.setRemark(text(req.getRemark()));
        assignment.setCapacityCounted(true);
        assignmentMapper.insert(assignment);

        ApsDepartmentTask departmentTask = new ApsDepartmentTask();
        departmentTask.setWorkItemId(workItemId);
        departmentTask.setDepartmentAssignmentId(assignment.getId());
        departmentTask.setDepartmentId(departmentId);
        departmentTask.setDepartmentName(department.getDepartmentName());
        departmentTask.setRequiredHours(requiredHours);
        departmentTask.setTaskStatus("ACTIVE");
        departmentTask.setAssignedBy(text(req.getAssignedBy()));
        departmentTask.setRemark(text(req.getRemark()));
        departmentTaskMapper.insert(departmentTask);

        workItemMapper.markAssigned(workItemId, departmentId, department.getDepartmentName());
        return getTaskDetail(workItemId);
    }

    @Transactional
    public Map<String, Object> assignDepartments(WorkItemBulkAssignReq req) {
        Set<Long> ids = normalizeIds(req == null ? null : req.getIds());
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Select at least one APS task to assign.");
        }

        DepartmentAssignmentReq assignmentReq = new DepartmentAssignmentReq();
        assignmentReq.setDepartmentId(req.getDepartmentId());
        assignmentReq.setAssignedBy(req.getAssignedBy());
        assignmentReq.setRemark(req.getRemark());

        List<Long> assignedIds = new ArrayList<>();
        for (Long workItemId : ids) {
            assignDepartment(workItemId, assignmentReq);
            assignedIds.add(workItemId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("assignedCount", assignedIds.size());
        result.put("assignedIds", assignedIds);
        return result;
    }

    @Transactional
    public ApsPmcTaskDetailDTO releaseAssignment(Long workItemId) {
        findWorkItem(workItemId, true);
        releaseActiveAssignment(workItemId);
        workItemMapper.markPendingAssignment(workItemId);
        return getTaskDetail(workItemId);
    }

    private void releaseActiveAssignment(Long workItemId) {
        ApsDepartmentAssignment active = assignmentMapper.selectActiveByWorkItemIdForUpdate(workItemId);
        if (active == null) {
            return;
        }
        BigDecimal requiredHours = decimal(active.getRequiredHours());
        Long departmentId = active.getDepartmentId();
        boolean capacityCounted = Boolean.TRUE.equals(active.getCapacityCounted());
        if (departmentId != null && capacityCounted) {
            departmentMapper.subtractOccupiedHours(departmentId, requiredHours);
        }
        departmentTaskMapper.releaseActiveByWorkItemId(workItemId);
        assignmentMapper.releaseById(active.getId());
    }

    private void releasePartLocks(Long workItemId) {
        ApsPartInventoryLock lock = partInventoryLockMapper.selectByWorkItemIdForUpdate(workItemId);
        if (lock == null) {
            return;
        }
        Long inventoryId = lock.getPartInventoryId();
        if (inventoryId != null) {
            int lockedInStock = numberInt(lock.getLockedInStockQty());
            int lockedInProduction = numberInt(lock.getLockedInProductionQty());
            if (lockedInStock > 0 || lockedInProduction > 0) {
                partInventoryMapper.releaseLockedQuantities(inventoryId, lockedInStock, lockedInProduction);
            }
            if (lockedInProduction > 0) {
                int releasedByDetail = releasePendingPartLockDetails(workItemId);
                int remaining = lockedInProduction - releasedByDetail;
                if (remaining > 0) {
                    releasePendingPartLocks(inventoryId, remaining);
                }
            }
        }
    }

    private int releasePendingPartLockDetails(Long workItemId) {
        int releasedQty = 0;
        List<ApsPartInventoryLockDetail> details =
                partInventoryLockDetailMapper.selectByWorkItemIdForUpdate(workItemId);
        for (ApsPartInventoryLockDetail detail : details) {
            if (!"IN_PRODUCTION".equals(detail.getLockType()) || detail.getPendingStockId() == null) {
                continue;
            }
            int lockedQty = numberInt(detail.getLockedQty());
            if (lockedQty <= 0) {
                continue;
            }
            partInventoryPendingStockMapper.releaseLockedQty(detail.getPendingStockId(), lockedQty);
            releasedQty += lockedQty;
        }
        return releasedQty;
    }

    private void releasePendingPartLocks(Long inventoryId, int releaseQty) {
        int remaining = releaseQty;
        List<ApsPartInventoryPendingStock> pendingRows =
                partInventoryPendingStockMapper.selectLockedRowsForUpdate(inventoryId);
        for (ApsPartInventoryPendingStock row : pendingRows) {
            if (remaining <= 0) {
                break;
            }
            int released = Math.min(remaining, numberInt(row.getLockedQty()));
            if (released <= 0) {
                continue;
            }
            partInventoryPendingStockMapper.releaseLockedQty(row.getId(), released);
            remaining -= released;
        }
    }

    private void releaseMaterialLocks(Long workItemId) {
        ApsMaterialLock lock = materialLockMapper.selectByWorkItemIdForUpdate(workItemId);
        if (lock == null) {
            return;
        }
        Long inventoryId = lock.getMaterialInventoryId();
        BigDecimal lockedWeight = decimal(lock.getLockedWeightKg());
        if (inventoryId != null && lockedWeight.compareTo(BigDecimal.ZERO) > 0) {
            materialInventoryMapper.releaseLockedWeight(inventoryId, lockedWeight);
        }
    }

    private Set<Long> normalizeIds(List<Long> ids) {
        Set<Long> result = new LinkedHashSet<>();
        if (ids == null) {
            return result;
        }
        for (Long id : ids) {
            if (id != null && id > 0) {
                result.add(id);
            }
        }
        return result;
    }

    private BigDecimal requiredHours(Long workItemId) {
        BigDecimal hours = workItemProcessMapper.sumWorkHoursByWorkItemId(workItemId);
        return hours == null ? BigDecimal.ZERO : hours;
    }

    private ApsDepartment findDepartmentForUpdate(Long id) {
        requireId(id, "department id");
        ApsDepartment department = departmentMapper.selectByIdForUpdate(id);
        if (department == null) {
            throw new IllegalArgumentException("Department not found.");
        }
        return department;
    }

    private ApsWorkItemDTO findWorkItem(Long id, boolean forUpdate) {
        requireId(id, "work item id");
        ApsWorkItemDTO item = forUpdate
                ? workItemMapper.selectDetailDtoByIdForUpdate(id)
                : workItemMapper.selectDetailDtoById(id);
        if (item == null) {
            throw new IllegalArgumentException("APS work item not found.");
        }
        return item;
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

    private String normalizeSortMode(String sortMode) {
        String value = text(sortMode).toUpperCase();
        return switch (value) {
            case "MATERIAL", "PROCESS", "PROCESS_MATCH" -> value;
            default -> "";
        };
    }

    private Long requireIdValue(Long id, String fieldName) {
        requireId(id, fieldName);
        return id;
    }

    private void requireId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private int numberInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
