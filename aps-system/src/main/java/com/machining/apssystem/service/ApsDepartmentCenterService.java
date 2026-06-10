package com.machining.apssystem.service;

import com.machining.apssystem.dto.ApsPmcTaskDetailDTO;
import com.machining.apssystem.dto.ApsWorkItemPageDTO;
import com.machining.apssystem.mapper.ApsDepartmentTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ApsDepartmentCenterService {
    @Autowired
    private ApsDepartmentTaskMapper departmentTaskMapper;

    @Autowired
    private ApsPmcService pmcService;

    public ApsWorkItemPageDTO listDepartmentTaskPage(Long departmentId, String status, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        String safeStatus = normalizeStatus(status);

        ApsWorkItemPageDTO result = new ApsWorkItemPageDTO();
        result.setPage(safePage);
        result.setPageSize(safePageSize);
        result.setTotal(departmentTaskMapper.countDepartmentTaskPage(departmentId, safeStatus));
        result.setRecords(departmentTaskMapper.selectDepartmentTaskPageDtos(
                departmentId, safeStatus, safePageSize, (safePage - 1) * safePageSize));
        return result;
    }

    public ApsPmcTaskDetailDTO getDepartmentTaskDetail(Long workItemId) {
        return pmcService.getTaskDetail(workItemId);
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

    private String normalizeStatus(String status) {
        String value = text(status).toUpperCase();
        if (value.isEmpty()) {
            return "ACTIVE";
        }
        return switch (value) {
            case "ACTIVE", "RELEASED" -> value;
            case "ALL" -> "";
            default -> "ACTIVE";
        };
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
