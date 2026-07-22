package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.dto.PartVersionKey;
import com.machining.quote.entity.ItemQuoteVersion;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface ItemQuoteVersionMapper extends BaseMapper<ItemQuoteVersion> {
    @ResultMap("mybatis-plus_ItemQuoteVersion")
    @Select("SELECT * FROM item_quote_version WHERE id = #{id} FOR UPDATE")
    ItemQuoteVersion selectByIdForUpdate(@Param("id") Integer id);

    @ResultMap("mybatis-plus_ItemQuoteVersion")
    @SelectProvider(type = RepeatQuoteSourceSqlProvider.class, method = "selectLatestReleasedRepeatSources")
    List<ItemQuoteVersion> selectLatestReleasedRepeatSources(
            @Param("currentRfqId") Integer currentRfqId,
            @Param("keys") List<PartVersionKey> keys);
}
