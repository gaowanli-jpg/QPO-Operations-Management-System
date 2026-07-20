package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.SystemPostDTO;
import com.machining.quote.dto.SystemPostSaveReq;
import com.machining.quote.entity.SystemPost;
import com.machining.quote.entity.SystemUser;
import com.machining.quote.mapper.SystemPostMapper;
import com.machining.quote.mapper.SystemUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SystemPostService {
    private static final Logger log = LoggerFactory.getLogger(SystemPostService.class);

    @Autowired
    private SystemPostMapper postMapper;
    @Autowired
    private SystemUserMapper userMapper;
    @Autowired
    private AuditService auditService;

    public List<SystemPostDTO> listPosts() {
        QueryWrapper<SystemPost> query = baseQuery();
        query.orderByAsc("sort_order").orderByAsc("id");
        return postMapper.selectList(query).stream().map(this::toDto).toList();
    }

    public List<SystemPostDTO> listPostOptions() {
        QueryWrapper<SystemPost> query = baseQuery();
        query.eq("enabled", true).orderByAsc("sort_order").orderByAsc("id");
        return postMapper.selectList(query).stream().map(this::toDto).toList();
    }

    @Transactional
    public SystemPostDTO savePost(SystemPostSaveReq req) {
        String postCode = normalizeCode(req == null ? null : req.getPostCode());
        String postName = trim(req == null ? null : req.getPostName());
        if (postCode.isEmpty()) {
            throw new RuntimeException("岗位编码不能为空");
        }
        if (postName == null) {
            throw new RuntimeException("岗位名称不能为空");
        }
        assertPostCodeUnique(postCode, req.getId());

        boolean creating = req.getId() == null;
        SystemPost post = creating ? new SystemPost() : requirePost(req.getId());
        Map<String, Object> before = creating ? null : snapshot(post);
        post.setPostCode(postCode);
        post.setPostName(postName);
        post.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        post.setEnabled(req.getEnabled() == null || req.getEnabled());
        post.setDeleted(false);
        post.setUpdatedAt(LocalDateTime.now());

        if (creating) {
            post.setCreatedAt(LocalDateTime.now());
            postMapper.insert(post);
            auditService.record("SYSTEM_POST_CREATE", "system_post", post.getId(), null, snapshot(post));
            log.info("Post created postId={} postCode={} postName={}", post.getId(), post.getPostCode(), post.getPostName());
        } else {
            postMapper.updateById(post);
            auditService.record("SYSTEM_POST_UPDATE", "system_post", post.getId(), before, snapshot(post));
            log.info("Post updated postId={} postCode={} postName={}", post.getId(), post.getPostCode(), post.getPostName());
        }
        return toDto(post);
    }

    @Transactional
    public SystemPostDTO updateStatus(Long id, Boolean enabled) {
        SystemPost post = requirePost(id);
        Map<String, Object> before = snapshot(post);
        post.setEnabled(Boolean.TRUE.equals(enabled));
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.updateById(post);
        String action = Boolean.TRUE.equals(post.getEnabled()) ? "SYSTEM_POST_ENABLE" : "SYSTEM_POST_DISABLE";
        auditService.record(action, "system_post", id, before, snapshot(post));
        log.info("Post status updated postId={} enabled={}", id, post.getEnabled());
        return toDto(post);
    }

    @Transactional
    public void deletePost(Long id) {
        SystemPost post = requirePost(id);
        if (countUsers(id) > 0) {
            log.warn("Post delete rejected because users exist postId={}", id);
            throw new RuntimeException("存在用户使用该岗位，不能删除");
        }
        Map<String, Object> before = snapshot(post);
        post.setDeleted(true);
        post.setEnabled(false);
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.updateById(post);
        auditService.record("SYSTEM_POST_DELETE", "system_post", id, before, null);
        log.info("Post deleted postId={} postCode={}", id, post.getPostCode());
    }

    private SystemPost requirePost(Long id) {
        if (id == null) {
            throw new RuntimeException("岗位 ID 不能为空");
        }
        SystemPost post = postMapper.selectById(id);
        if (post == null || Boolean.TRUE.equals(post.getDeleted())) {
            throw new RuntimeException("岗位不存在");
        }
        return post;
    }

    private QueryWrapper<SystemPost> baseQuery() {
        QueryWrapper<SystemPost> query = new QueryWrapper<>();
        query.and(wrapper -> wrapper.eq("deleted", false).or().isNull("deleted"));
        return query;
    }

    private void assertPostCodeUnique(String postCode, Long currentId) {
        QueryWrapper<SystemPost> query = baseQuery();
        query.eq("post_code", postCode);
        if (currentId != null) {
            query.ne("id", currentId);
        }
        if (postMapper.selectCount(query) > 0) {
            throw new RuntimeException("岗位编码已存在");
        }
    }

    private long countUsers(Long id) {
        QueryWrapper<SystemUser> query = new QueryWrapper<>();
        query.eq("post_id", id).and(wrapper -> wrapper.eq("deleted", false).or().isNull("deleted"));
        return userMapper.selectCount(query);
    }

    private SystemPostDTO toDto(SystemPost post) {
        SystemPostDTO dto = new SystemPostDTO();
        dto.setId(post.getId());
        dto.setPostCode(post.getPostCode());
        dto.setPostName(post.getPostName());
        dto.setSortOrder(post.getSortOrder());
        dto.setEnabled(post.getEnabled());
        return dto;
    }

    private Map<String, Object> snapshot(SystemPost post) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", post.getId());
        row.put("postCode", post.getPostCode());
        row.put("postName", post.getPostName());
        row.put("sortOrder", post.getSortOrder());
        row.put("enabled", post.getEnabled());
        return row;
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
