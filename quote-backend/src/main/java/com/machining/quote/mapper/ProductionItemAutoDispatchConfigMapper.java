package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.ProductionItemAutoDispatchConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface ProductionItemAutoDispatchConfigMapper extends BaseMapper<ProductionItemAutoDispatchConfig> {

    @Update("""
            UPDATE production_item_auto_dispatch_config
            SET last_run_at = #{runAt},
                last_run_date = #{runDate},
                updated_at = #{runAt}
            WHERE id = #{id}
              AND (last_run_date IS NULL OR last_run_date < #{runDate})
            """)
    int claimDailyRun(
            @Param("id") String id,
            @Param("runAt") LocalDateTime runAt,
            @Param("runDate") LocalDate runDate);

    @Update("""
            UPDATE production_item_auto_dispatch_config
            SET last_run_date = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int resetDailyRunClaim(@Param("id") String id);
}
