package com.machining.apssystem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.machining.apssystem.dto.ApsDepartmentDTO;
import com.machining.apssystem.dto.DepartmentCapacityAdjustReq;
import com.machining.apssystem.dto.DepartmentCapacityResetReq;
import com.machining.apssystem.dto.DepartmentSaveReq;
import com.machining.apssystem.entity.ApsDepartment;
import com.machining.apssystem.mapper.ApsDepartmentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApsDepartmentService {
    @Autowired
    private ApsDepartmentMapper departmentMapper;

    public List<ApsDepartmentDTO> listDepartments(String type, String status) {
        String safeType = text(type).toUpperCase();
        String safeStatus = text(status).toUpperCase();
        LambdaQueryWrapper<ApsDepartment> wrapper = new LambdaQueryWrapper<>();
        if (!safeType.isEmpty()) {
            wrapper.eq(ApsDepartment::getDepartmentType, safeType);
        }
        if (!safeStatus.isEmpty()) {
            wrapper.eq(ApsDepartment::getDepartmentStatus, safeStatus);
        }
        wrapper.orderByAsc(ApsDepartment::getDepartmentType)
                .orderByAsc(ApsDepartment::getDepartmentName)
                .orderByAsc(ApsDepartment::getId);
        return departmentMapper.selectList(wrapper).stream()
                .map(ApsDepartmentDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public ApsDepartmentDTO saveDepartment(DepartmentSaveReq req) {
        String departmentName = requireText(req == null ? null : req.getDepartmentName(), "departmentName");
        if (findByDepartmentName(departmentName) != null) {
            throw new IllegalStateException("Department name already exists.");
        }
        ApsDepartment department = new ApsDepartment();
        department.setDepartmentName(departmentName);
        department.setDepartmentType(departmentType(req.getDepartmentType()));
        department.setDepartmentStatus(departmentStatus(req.getDepartmentStatus()));
        department.setMonthlyAvailableHours(nonNegativeOrNull(req.getMonthlyAvailableHours()));
        department.setRemark(text(req.getRemark()));
        departmentMapper.insert(department);
        ApsDepartment saved = findDepartment(department.getId());
        validateDepartmentCapacity(saved);
        return ApsDepartmentDTO.from(saved);
    }

    @Transactional
    public ApsDepartmentDTO updateDepartment(Long id, DepartmentSaveReq req) {
        requireId(id, "department id");
        ApsDepartment department = departmentMapper.selectById(id);
        if (department == null) {
            throw new IllegalArgumentException("Department not found.");
        }
        department.setDepartmentName(requireText(req == null ? null : req.getDepartmentName(), "departmentName"));
        department.setDepartmentType(departmentType(req.getDepartmentType()));
        department.setDepartmentStatus(departmentStatus(req.getDepartmentStatus()));
        department.setMonthlyAvailableHours(nonNegativeOrNull(req.getMonthlyAvailableHours()));
        department.setRemark(text(req.getRemark()));
        department.setUpdatedAt(LocalDateTime.now());
        departmentMapper.updateById(department);

        ApsDepartment saved = findDepartment(id);
        validateDepartmentCapacity(saved);
        return ApsDepartmentDTO.from(saved);
    }

    @Transactional
    public ApsDepartmentDTO adjustDepartmentCapacity(Long id, DepartmentCapacityAdjustReq req) {
        ApsDepartment department = findDepartmentForUpdate(id);
        BigDecimal deltaHours = requireDeltaHours(req == null ? null : req.getDeltaHours());
        BigDecimal currentMonthly = decimal(department.getMonthlyAvailableHours());
        BigDecimal occupied = decimal(department.getOccupiedHours());
        BigDecimal nextMonthly = currentMonthly.add(deltaHours);
        if (nextMonthly.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Monthly available hours cannot be negative.");
        }
        if (nextMonthly.compareTo(occupied) < 0) {
            throw new IllegalStateException("Monthly available hours cannot be lower than used hours.");
        }
        department.setMonthlyAvailableHours(nextMonthly);
        department.setUpdatedAt(LocalDateTime.now());
        departmentMapper.updateById(department);
        return ApsDepartmentDTO.from(findDepartment(id));
    }

    @Transactional
    public ApsDepartmentDTO resetDepartmentCapacity(Long id, DepartmentCapacityResetReq req) {
        ApsDepartment department = findDepartmentForUpdate(id);
        BigDecimal monthlyAvailableHours = nonNegativeRequired(
                req == null ? null : req.getMonthlyAvailableHours(), "monthlyAvailableHours");
        departmentMapper.resetActiveAssignmentCapacityCounted(id);
        department.setMonthlyAvailableHours(monthlyAvailableHours);
        department.setOccupiedHours(BigDecimal.ZERO);
        department.setUpdatedAt(LocalDateTime.now());
        departmentMapper.updateById(department);
        return ApsDepartmentDTO.from(findDepartment(id));
    }

    @Transactional
    public void deleteDepartment(Long id) {
        ApsDepartment department = findDepartment(id);
        BigDecimal occupied = decimal(department.getOccupiedHours());
        if (occupied.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Department has occupied hours and cannot be deleted.");
        }
        if (departmentMapper.countActiveAssignments(id) > 0) {
            throw new IllegalStateException("Department has active assignments and cannot be deleted.");
        }
        departmentMapper.clearInactiveAssignmentDepartment(id);
        departmentMapper.deleteById(id);
    }

    private ApsDepartment findByDepartmentName(String departmentName) {
        LambdaQueryWrapper<ApsDepartment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApsDepartment::getDepartmentName, departmentName)
                .last("LIMIT 1");
        return departmentMapper.selectOne(wrapper);
    }

    private ApsDepartment findDepartment(Long id) {
        requireId(id, "department id");
        ApsDepartment department = departmentMapper.selectById(id);
        if (department == null) {
            throw new IllegalArgumentException("Department not found.");
        }
        return department;
    }

    private ApsDepartment findDepartmentForUpdate(Long id) {
        requireId(id, "department id");
        ApsDepartment department = departmentMapper.selectByIdForUpdate(id);
        if (department == null) {
            throw new IllegalArgumentException("Department not found.");
        }
        return department;
    }

    private String departmentType(String value) {
        String text = defaultText(value, "PRODUCTION").toUpperCase();
        if (!"PRODUCTION".equals(text) && !"OUTSOURCE".equals(text)) {
            throw new IllegalArgumentException("departmentType must be PRODUCTION or OUTSOURCE.");
        }
        return text;
    }

    private String departmentStatus(String value) {
        String text = defaultText(value, "ENABLED").toUpperCase();
        if (!"ENABLED".equals(text) && !"DISABLED".equals(text)) {
            throw new IllegalArgumentException("departmentStatus must be ENABLED or DISABLED.");
        }
        return text;
    }

    private void validateDepartmentCapacity(ApsDepartment department) {
        BigDecimal monthlyAvailable = decimal(department.getMonthlyAvailableHours());
        BigDecimal occupied = decimal(department.getOccupiedHours());
        if (monthlyAvailable.compareTo(occupied) < 0) {
            throw new IllegalStateException("Monthly available hours cannot be lower than used hours.");
        }
    }

    private BigDecimal nonNegativeOrNull(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Hours cannot be negative.");
        }
        return value;
    }

    private BigDecimal nonNegativeRequired(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative.");
        }
        return value;
    }

    private BigDecimal requireDeltaHours(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("deltaHours is required.");
        }
        return value;
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

    private String defaultText(String value, String fallback) {
        String text = text(value);
        return text.isEmpty() ? fallback : text;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
