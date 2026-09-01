package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.PricingOptionUsageStat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.annotations.Lang;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PricingOptionUsageStatMapper extends BaseMapper<PricingOptionUsageStat> {
    @Insert("""
            INSERT INTO pricing_option_usage_stat (option_type, option_key, usage_count, created_at, updated_at)
            VALUES (#{optionType}, #{optionKey}, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (option_type, option_key)
            DO UPDATE SET usage_count = pricing_option_usage_stat.usage_count + 1,
                          updated_at = CURRENT_TIMESTAMP
            """)
    void increment(@Param("optionType") String optionType, @Param("optionKey") String optionKey);

    @Select("""
            <script>
            SELECT option_key, usage_count
            FROM pricing_option_usage_stat
            WHERE option_type = #{optionType}
              AND option_key IN
              <foreach collection="optionKeys" item="optionKey" open="(" separator="," close=")">
                #{optionKey}
              </foreach>
            </script>
            """)
    @Lang(XMLLanguageDriver.class)
    List<PricingOptionUsageStat> selectUsageCounts(
            @Param("optionType") String optionType,
            @Param("optionKeys") Collection<String> optionKeys);
}
