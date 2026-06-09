package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.entity.ApsProcess;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApsProcessMapper extends BaseMapper<ApsProcess> {
    @Insert("""
            INSERT INTO aps_process (
                process_name, process_type, calc_type, erp_source_id, sort_no, synced_at
            ) VALUES (
                #{processName}, #{processType}, #{calcType}, #{erpSourceId}, #{sortNo}, CURRENT_TIMESTAMP
            )
            ON CONFLICT (process_name, process_type)
            DO UPDATE SET
                calc_type = EXCLUDED.calc_type,
                erp_source_id = EXCLUDED.erp_source_id,
                sort_no = EXCLUDED.sort_no,
                synced_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            """)
    int upsertFromErp(ApsProcess process);
}
