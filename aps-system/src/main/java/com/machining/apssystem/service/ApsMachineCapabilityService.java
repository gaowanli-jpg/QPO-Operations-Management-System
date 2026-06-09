package com.machining.apssystem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.machining.apssystem.dto.ApsMachineCapabilityDTO;
import com.machining.apssystem.dto.ApsMachineDTO;
import com.machining.apssystem.dto.MachineCapabilitySaveReq;
import com.machining.apssystem.dto.MachineSaveReq;
import com.machining.apssystem.entity.ApsMachine;
import com.machining.apssystem.entity.ApsMachineProcessCapability;
import com.machining.apssystem.entity.ApsProcess;
import com.machining.apssystem.mapper.ApsMachineCapabilityMapper;
import com.machining.apssystem.mapper.ApsMachineMapper;
import com.machining.apssystem.mapper.ApsProcessMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ApsMachineCapabilityService {
    @Autowired
    private ApsMachineMapper machineMapper;

    @Autowired
    private ApsMachineCapabilityMapper capabilityMapper;

    @Autowired
    private ApsProcessMapper processMapper;

    public List<ApsMachineDTO> listMachines(Long departmentId) {
        if (departmentId == null || departmentId <= 0) {
            return machineMapper.selectMachineDtos();
        }
        return machineMapper.selectMachineDtosByDepartmentId(departmentId);
    }

    @Transactional
    public ApsMachineDTO saveMachine(MachineSaveReq req) {
        Long departmentId = requireIdValue(req == null ? null : req.getDepartmentId(), "departmentId");
        requireDepartmentExists(departmentId);

        String machineCode = text(req.getMachineCode());
        if (!machineCode.isEmpty() && findMachineByDepartmentAndCode(departmentId, machineCode) != null) {
            throw new IllegalStateException("Machine code already exists in this department.");
        }
        ApsMachine machine = new ApsMachine();
        machine.setDepartmentId(departmentId);
        machine.setMachineCode(machineCode);
        machine.setMachineName(requireText(req.getMachineName(), "machineName"));
        machine.setMachineStatus(defaultText(req.getMachineStatus(), "IDLE"));
        machine.setRemark(text(req.getRemark()));
        machineMapper.insert(machine);
        return findMachineDto(machine.getId());
    }

    @Transactional
    public ApsMachineDTO updateMachine(Long id, MachineSaveReq req) {
        requireId(id, "machine id");
        Long departmentId = requireIdValue(req == null ? null : req.getDepartmentId(), "departmentId");
        requireDepartmentExists(departmentId);
        ApsMachine existing = machineMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Machine not found.");
        }

        existing.setDepartmentId(departmentId);
        existing.setMachineCode(text(req.getMachineCode()));
        existing.setMachineName(requireText(req.getMachineName(), "machineName"));
        existing.setMachineStatus(defaultText(req.getMachineStatus(), "IDLE"));
        existing.setRemark(text(req.getRemark()));
        existing.setUpdatedAt(LocalDateTime.now());
        machineMapper.updateById(existing);
        return findMachineDto(id);
    }

    @Transactional
    public void deleteMachine(Long id) {
        requireId(id, "machine id");
        int deleted = machineMapper.deleteById(id);
        if (deleted == 0) {
            throw new IllegalArgumentException("Machine not found.");
        }
    }

    public List<ApsMachineCapabilityDTO> listMachineCapabilities(Long machineId) {
        if (machineId == null || machineId <= 0) {
            return capabilityMapper.selectCapabilityDtos();
        }
        return capabilityMapper.selectCapabilityDtosByMachineId(machineId);
    }

    @Transactional
    public ApsMachineCapabilityDTO saveMachineCapability(MachineCapabilitySaveReq req) {
        Long machineId = requireIdValue(req == null ? null : req.getMachineId(), "machineId");
        requireMachineExists(machineId);
        ApsProcess process = resolveCapabilityProcess(req);

        if (findCapabilityByMachineAndProcess(machineId, process.getProcessName(), process.getCalcType()) != null) {
            throw new IllegalStateException("Machine capability already exists.");
        }
        ApsMachineProcessCapability capability = new ApsMachineProcessCapability();
        capability.setMachineId(machineId);
        capability.setProcessName(process.getProcessName());
        capability.setCalcType(process.getCalcType());
        capability.setProcessId(process.getId());
        capability.setRemark(text(req.getRemark()));
        capabilityMapper.insert(capability);
        return findCapabilityDto(capability.getId());
    }

    @Transactional
    public void deleteMachineCapability(Long id) {
        requireId(id, "capability id");
        int deleted = capabilityMapper.deleteById(id);
        if (deleted == 0) {
            throw new IllegalArgumentException("Machine capability not found.");
        }
    }

    private ApsMachine findMachineByDepartmentAndCode(Long departmentId, String machineCode) {
        LambdaQueryWrapper<ApsMachine> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApsMachine::getDepartmentId, departmentId)
                .eq(ApsMachine::getMachineCode, machineCode)
                .last("LIMIT 1");
        return machineMapper.selectOne(wrapper);
    }

    private ApsMachineProcessCapability findCapabilityByMachineAndProcess(
            Long machineId, String processName, String calcType) {
        LambdaQueryWrapper<ApsMachineProcessCapability> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApsMachineProcessCapability::getMachineId, machineId)
                .eq(ApsMachineProcessCapability::getProcessName, processName);
        if (calcType == null) {
            wrapper.isNull(ApsMachineProcessCapability::getCalcType);
        } else {
            wrapper.eq(ApsMachineProcessCapability::getCalcType, calcType);
        }
        wrapper.last("LIMIT 1");
        return capabilityMapper.selectOne(wrapper);
    }

    private ApsProcess resolveCapabilityProcess(MachineCapabilitySaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("processId is required.");
        }
        Long processId = req.getProcessId();
        if (processId != null && processId > 0) {
            ApsProcess process = processMapper.selectById(processId);
            if (process == null) {
                throw new IllegalArgumentException("Process not found. Sync processes from ERP first.");
            }
            return process;
        }

        String processName = requireText(req.getProcessName(), "processName");
        String processType = processTypeFromCalcType(req.getCalcType());
        LambdaQueryWrapper<ApsProcess> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApsProcess::getProcessName, processName)
                .eq(ApsProcess::getProcessType, processType)
                .last("LIMIT 1");
        ApsProcess process = processMapper.selectOne(wrapper);
        if (process == null) {
            throw new IllegalArgumentException("Process is not synced to APS. Sync processes from ERP first.");
        }
        return process;
    }

    private ApsMachineDTO findMachineDto(Long id) {
        ApsMachineDTO machine = machineMapper.selectMachineDtoById(id);
        if (machine == null) {
            throw new IllegalArgumentException("Machine not found.");
        }
        return machine;
    }

    private ApsMachineCapabilityDTO findCapabilityDto(Long id) {
        ApsMachineCapabilityDTO capability = capabilityMapper.selectCapabilityDtoById(id);
        if (capability == null) {
            throw new IllegalArgumentException("Machine capability not found.");
        }
        return capability;
    }

    private void requireDepartmentExists(Long id) {
        if (machineMapper.countDepartmentById(id) == 0) {
            throw new IllegalArgumentException("Department not found.");
        }
    }

    private void requireMachineExists(Long id) {
        requireId(id, "machine id");
        if (machineMapper.selectById(id) == null) {
            throw new IllegalArgumentException("Machine not found.");
        }
    }

    private String processTypeFromCalcType(String value) {
        String text = text(value);
        if ("NON_WORK_HOUR".equalsIgnoreCase(text) || "\u975e\u5de5\u65f6\u8d39\u7528".equals(text)) {
            return "NON_WORK_HOUR";
        }
        return "WORK_HOUR";
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
}
