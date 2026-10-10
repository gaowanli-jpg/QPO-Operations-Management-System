package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.WmsReservationBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WmsReservationBatchMapper extends BaseMapper<WmsReservationBatch> {
    @Select("SELECT * FROM wms_reservation_batch WHERE external_biz_id = #{externalBizId} FOR UPDATE")
    WmsReservationBatch selectByExternalBizIdForUpdate(@Param("externalBizId") String externalBizId);

    @Select("""
            <script>
            SELECT * FROM wms_reservation_batch
            WHERE id IN
            <foreach collection="batchIds" item="batchId" open="(" separator="," close=")">
                #{batchId}
            </foreach>
            </script>
            """)
    List<WmsReservationBatch> selectLinkedChangeBatchesByIds(@Param("batchIds") List<Long> batchIds);
}
