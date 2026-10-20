package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.SalesOrderHeader;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalesOrderHeaderMapper extends BaseMapper<SalesOrderHeader> {
    @Select("SELECT * FROM sales_order_header WHERE id = #{id} FOR UPDATE")
    SalesOrderHeader selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{batchKey}, 0))) AS batch_lock")
    Integer lockReleaseBatch(@Param("batchKey") String batchKey);

    @Select("SELECT * FROM sales_order_header WHERE source_release_batch_key = #{batchKey}")
    SalesOrderHeader selectByReleaseBatchKey(@Param("batchKey") String batchKey);
}
