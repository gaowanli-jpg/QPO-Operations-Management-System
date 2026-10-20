package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.SystemDeptDTO;
import com.machining.quote.dto.SystemDeptSaveReq;
import com.machining.quote.entity.SystemDept;
import com.machining.quote.entity.SystemUser;
import com.machining.quote.mapper.SystemDeptMapper;
import com.machining.quote.mapper.SystemUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SystemDeptService {
    private static final Logger log = LoggerFactory.getLogger(SystemDeptService.class);

    @Autowired
    private SystemDeptMapper deptMapper;
    @Autowired
    private SystemUserMapper userMapper;
    @Autowired
    private AuditService auditService;

    public List<SystemDeptDTO> listDeptTree() {
        QueryWrapper<SystemDept> query = baseQuery();
        query.orderByAsc("sort_order").orderByAsc("id");
        return buildTree(deptMapper.selectList(query));
    }

    public List<SystemDeptDTO> listDeptOptions() {
        QueryWrapper<SystemDept> query = baseQuery();
        query.eq("enabled", true).orderByAsc("sort_order").orderByAsc("id");
        return deptMapper.selectList(query).stream().map(this::toDto).toList();
    }

    @Transactional
    public SystemDeptDTO saveDept(SystemDeptSaveReq req) {
        String deptCode = normalizeCode(req == null ? null : req.getDeptCode());
        String deptName = trim(req == null ? null : req.getDeptName());
        if (deptCode.isEmpty()) {
            throw new RuntimeException("部门编码不能为空");
        }
        if (deptName == null) {
            throw new RuntimeException("部门名称不能为空");
        }
        Long parentId = normalizeParentId(req.getParentId());
        if (parentId > 0) {
            requireDept(parentId);
        }
        assertDeptCodeUnique(deptCode, req.getId());

        boolean creating = req.getId() == null;
        SystemDept dept = creating ? new SystemDept() : requireDept(req.getId());
        Map<String, Object> before = creating ? null : snapshot(dept);
        dept.setParentId(parentId);
        dept.setDeptCode(deptCode);
        dept.setDeptName(deptName);
        dept.setLeaderUserId(req.getLeaderUserId());
        dept.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        dept.setEnabled(req.getEnabled() == null || req.getEnabled());
        dept.setDeleted(false);
        dept.setUpdatedAt(LocalDateTime.now());

        if (creating) {
            dept.setCreatedAt(LocalDateTime.now());
            deptMapper.insert(dept);
            auditService.record("SYSTEM_DEPT_CREATE", "system_dept", dept.getId(), null, snapshot(dept));
            log.info("Department created deptId={} deptCode={} deptName={}", dept.getId(), dept.getDeptCode(), dept.getDeptName());
        } else {
            deptMapper.updateById(dept);
            auditService.record("SYSTEM_DEPT_UPDATE", "system_dept", dept.getId(), before, snapshot(dept));
            log.info("Department updated deptId={} deptCode={} deptName={}", dept.getId(), dept.getDeptCode(), dept.getDeptName());
        }
        return toDto(dept);
    }

    @Transactional
    public SystemDeptDTO updateStatus(Long id, Boolean enabled) {
        SystemDept dept = requireDept(id);
        Map<String, Object> before = snapshot(dept);
        dept.setEnabled(Boolean.TRUE.equals(enabled));
        dept.setUpdatedAt(LocalDateTime.now());
        deptMapper.updateById(dept);
        String action = Boolean.TRUE.equals(dept.getEnabled()) ? "SYSTEM_DEPT_ENABLE" : "SYSTEM_DEPT_DISABLE";
        auditService.record(action, "system_dept", id, before, snapshot(dept));
        log.info("Department status updated deptId={} enabled={}", id, dept.getEnabled());
        return toDto(dept);
    }

    @Transactional
    public void deleteDept(Long id) {
        SystemDept dept = requireDept(id);
        if (countChildren(id) > 0) {
            log.warn("Department delete rejected because child departments exist deptId={}", id);
            throw new RuntimeException("存在子部门，不能删除");
        }
        if (countUsers(id) > 0) {
            log.warn("Department delete rejected because users exist deptId={}", id);
            throw new RuntimeException("存在用户归属该部门，不能删除");
        }
        Map<String, Object> before = snapshot(dept);
        dept.setDeleted(true);
        dept.setEnabled(false);
        dept.setUpdatedAt(LocalDateTime.now());
        deptMapper.updateById(dept);
        auditService.record("SYSTEM_DEPT_DELETE", "system_dept", id, before, null);
        log.info("Department deleted deptId={} deptCode={}", id, dept.getDeptCode());
    }

    private SystemDept requireDept(Long id) {
        if (id == null) {
            throw new RuntimeException("部门 ID 不能为空");
        }
        SystemDept dept = deptMapper.selectById(id);
        if (dept == null || Boolean.TRUE.equals(dept.getDeleted())) {
            throw new RuntimeException("部门不存在");
        }
        return dept;
    }

    private QueryWrapper<SystemDept> baseQuery() {
        QueryWrapper<SystemDept> query = new QueryWrapper<>();
        query.and(wrapper -> wrapper.eq("deleted", false).or().isNull("deleted"));
        return query;
    }

    private void assertDeptCodeUnique(String deptCode, Long currentId) {
        QueryWrapper<SystemDept> query = baseQuery();
        query.eq("dept_code", deptCode);
        if (currentId != null) {
            query.ne("id", currentId);
        }
        if (deptMapper.selectCount(query) > 0) {
            throw new RuntimeException("部门编码已存在");
        }
    }

    private long countChildren(Long id) {
        QueryWrapper<SystemDept> query = baseQuery();
        query.eq("parent_id", id);
        return deptMapper.selectCount(query);
    }

    private long countUsers(Long id) {
        QueryWrapper<SystemUser> query = new QueryWrapper<>();
        query.eq("dept_id", id).and(wrapper -> wrapper.eq("deleted", false).or().isNull("deleted"));
        return userMapper.selectCount(query);
    }

    private Long normalizeParentId(Long parentId) {
        return parentId == null ? 0L : parentId;
    }

    private List<SystemDeptDTO> buildTree(List<SystemDept> depts) {
        Map<Long, SystemDeptDTO> byId = new LinkedHashMap<>();
        for (SystemDept dept : depts == null ? List.<SystemDept>of() : depts) {
            byId.put(dept.getId(), toDto(dept));
        }
        List<SystemDeptDTO> roots = new ArrayList<>();
        for (SystemDeptDTO dept : byId.values()) {
            Long parentId = dept.getParentId();
            if (parentId != null && parentId > 0 && byId.containsKey(parentId)) {
                byId.get(parentId).getChildren().add(dept);
            } else {
                roots.add(dept);
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<SystemDeptDTO> depts) {
        depts.sort(Comparator
                .comparing((SystemDeptDTO dept) -> dept.getSortOrder() == null ? Integer.MAX_VALUE : dept.getSortOrder())
                .thenComparing(dept -> dept.getId() == null ? Long.MAX_VALUE : dept.getId()));
        for (SystemDeptDTO dept : depts) {
            sortTree(dept.getChildren());
        }
    }

    private SystemDeptDTO toDto(SystemDept dept) {
        SystemDeptDTO dto = new SystemDeptDTO();
        dto.setId(dept.getId());
        dto.setParentId(dept.getParentId());
        dto.setDeptCode(dept.getDeptCode());
        dto.setDeptName(dept.getDeptName());
        dto.setLeaderUserId(dept.getLeaderUserId());
        dto.setSortOrder(dept.getSortOrder());
        dto.setEnabled(dept.getEnabled());
        return dto;
    }

    private Map<String, Object> snapshot(SystemDept dept) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", dept.getId());
        row.put("parentId", dept.getParentId());
        row.put("deptCode", dept.getDeptCode());
        row.put("deptName", dept.getDeptName());
        row.put("leaderUserId", dept.getLeaderUserId());
        row.put("sortOrder", dept.getSortOrder());
        row.put("enabled", dept.getEnabled());
        return row;
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
