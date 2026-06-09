package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsMachineDTO;
import com.machining.apssystem.entity.ApsMachine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApsMachineMapper extends BaseMapper<ApsMachine> {
    @Select("SELECT COUNT(*) FROM aps_department WHERE id = #{id}")
    int countDepartmentById(@Param("id") Long id);

    @Select("""
            SELECT m.id, m.department_id, d.department_name, m.machine_code, m.machine_name,
                   m.machine_status, m.remark, m.created_at, m.updated_at
            FROM aps_machine m
            JOIN aps_department d ON d.id = m.department_id
            ORDER BY d.department_name, m.machine_code, m.id
            """)
    List<ApsMachineDTO> selectMachineDtos();

    @Select("""
            SELECT m.id, m.department_id, d.department_name, m.machine_code, m.machine_name,
                   m.machine_status, m.remark, m.created_at, m.updated_at
            FROM aps_machine m
            JOIN aps_department d ON d.id = m.department_id
            WHERE m.department_id = #{departmentId}
            ORDER BY m.machine_code, m.id
            """)
    List<ApsMachineDTO> selectMachineDtosByDepartmentId(@Param("departmentId") Long departmentId);

    @Select("""
            SELECT m.id, m.department_id, d.department_name, m.machine_code, m.machine_name,
                   m.machine_status, m.remark, m.created_at, m.updated_at
            FROM aps_machine m
            JOIN aps_department d ON d.id = m.department_id
            WHERE m.id = #{id}
            """)
    ApsMachineDTO selectMachineDtoById(@Param("id") Long id);
}
