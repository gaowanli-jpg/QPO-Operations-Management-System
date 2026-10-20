package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.AuthDataCenterSsoNonce;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthDataCenterSsoNonceMapper extends BaseMapper<AuthDataCenterSsoNonce> {
    @Insert("""
            INSERT INTO auth_data_center_sso_nonce (
                nonce,
                nonce_hash,
                username,
                app_code,
                request_timestamp,
                used_at,
                expires_at,
                result,
                failure_reason,
                created_at
            ) VALUES (
                #{nonce},
                #{nonceHash},
                #{username},
                #{appCode},
                #{requestTimestamp},
                COALESCE(#{usedAt}, CURRENT_TIMESTAMP),
                #{expiresAt},
                #{result},
                #{failureReason},
                COALESCE(#{createdAt}, CURRENT_TIMESTAMP)
            )
            ON CONFLICT (nonce_hash) DO NOTHING
            """)
    int insertIgnore(AuthDataCenterSsoNonce row);
}
