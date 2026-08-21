package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dict_process_detail")
public class DictProcessDetail {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;
    private BigDecimal hourlyRate;
    private String detailName;
    private String detailRemarks;
    private Boolean enabled;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
