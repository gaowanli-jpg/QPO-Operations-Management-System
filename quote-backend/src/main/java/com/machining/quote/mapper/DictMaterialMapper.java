package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.DictMaterial;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DictMaterialMapper extends BaseMapper<DictMaterial> {
    // 继承了 BaseMapper 后，你连 SQL 都不用写了，增删改查它全包了！
}