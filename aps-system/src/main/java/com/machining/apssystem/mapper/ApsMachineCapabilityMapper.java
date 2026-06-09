package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsMachineCapabilityDTO;
import com.machining.apssystem.entity.ApsMachineProcessCapability;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApsMachineCapabilityMapper extends BaseMapper<ApsMachineProcessCapability> {
    @Select("""
            SELECT c.id, c.machine_id, m.machine_code, m.machine_name, d.department_name,
                   c.process_id, p.process_type, c.process_name, c.calc_type,
                   c.remark, c.created_at, c.updated_at
            FROM aps_machine_process_capability c
            JOIN aps_machine m ON m.id = c.machine_id
            JOIN aps_department d ON d.id = m.department_id
            LEFT JOIN aps_process p ON p.id = c.process_id
            ORDER BY d.department_name, m.machine_code, c.process_name, c.id
            """)
    List<ApsMachineCapabilityDTO> selectCapabilityDtos();

    @Select("""
            SELECT c.id, c.machine_id, m.machine_code, m.machine_name, d.department_name,
                   c.process_id, p.process_type, c.process_name, c.calc_type,
                   c.remark, c.created_at, c.updated_at
            FROM aps_machine_process_capability c
            JOIN aps_machine m ON m.id = c.machine_id
            JOIN aps_department d ON d.id = m.department_id
            LEFT JOIN aps_process p ON p.id = c.process_id
            WHERE c.machine_id = #{machineId}
            ORDER BY c.process_name, c.id
            """)
    List<ApsMachineCapabilityDTO> selectCapabilityDtosByMachineId(@Param("machineId") Long machineId);

    @Select("""
            SELECT c.id, c.machine_id, m.machine_code, m.machine_name, d.department_name,
                   c.process_id, p.process_type, c.process_name, c.calc_type,
                   c.remark, c.created_at, c.updated_at
            FROM aps_machine_process_capability c
            JOIN aps_machine m ON m.id = c.machine_id
            JOIN aps_department d ON d.id = m.department_id
            LEFT JOIN aps_process p ON p.id = c.process_id
            WHERE c.id = #{id}
            """)
    ApsMachineCapabilityDTO selectCapabilityDtoById(@Param("id") Long id);
}
