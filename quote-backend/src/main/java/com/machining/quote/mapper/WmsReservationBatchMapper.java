package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.WmsReservationBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WmsReservationBatchMapper extends BaseMapper<WmsReservationBatch> {
    @Select("SELECT * FROM wms_reservation_batch WHERE external_biz_id = #{externalBizId} FOR UPDATE")
    WmsReservationBatch selectByExternalBizIdForUpdate(@Param("externalBizId") String externalBizId);
}
