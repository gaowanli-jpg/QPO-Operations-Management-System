package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.MesProductionBatchItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MesProductionBatchItemMapper extends BaseMapper<MesProductionBatchItem> {
    @Select("""
            <script>
            SELECT item.*
            FROM mes_production_batch_item item
            JOIN (
                SELECT allocation_id, MAX(id) AS id
                FROM mes_production_batch_item
                WHERE allocation_id IN
                <foreach collection="allocationIds" item="allocationId" open="(" separator="," close=")">
                    #{allocationId}
                </foreach>
                GROUP BY allocation_id
            ) latest ON latest.id = item.id
            </script>
            """)
    List<MesProductionBatchItem> selectLatestByAllocationIds(@Param("allocationIds") List<Integer> allocationIds);

    /**
     * 记录需求任务撤销结果。保留台账行，不物理删除。
     */
    @Update("""
            UPDATE mes_production_batch_item
            SET status = #{status},
                message = #{message},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int markCancelledById(@Param("id") Long id,
                          @Param("status") String status,
                          @Param("message") String message);
}
