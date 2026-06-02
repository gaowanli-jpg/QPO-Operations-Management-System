package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.ItemQuoteVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ItemQuoteVersionMapper extends BaseMapper<ItemQuoteVersion> {
    @ResultMap("mybatis-plus_ItemQuoteVersion")
    @Select("SELECT * FROM item_quote_version WHERE id = #{id} FOR UPDATE")
    ItemQuoteVersion selectByIdForUpdate(@Param("id") Integer id);
}
