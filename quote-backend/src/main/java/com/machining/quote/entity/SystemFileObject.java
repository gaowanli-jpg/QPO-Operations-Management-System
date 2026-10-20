package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_file_object")
public class SystemFileObject {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String publicPath;
    private String storageType;
    private Long storageConfigId;
    private String objectKey;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private String externalUrl;
    private String bizType;
    private String bizId;
    private String createdBy;
    private LocalDateTime createdAt;
}
