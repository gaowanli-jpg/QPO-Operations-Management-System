package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.controller.SystemDeptController;
import com.machining.quote.controller.SystemPostController;
import com.machining.quote.dto.SystemDeptDTO;
import com.machining.quote.dto.SystemDeptSaveReq;
import com.machining.quote.dto.SystemPostDTO;
import com.machining.quote.dto.SystemPostSaveReq;
import com.machining.quote.entity.SystemDept;
import com.machining.quote.entity.SystemPost;
import com.machining.quote.mapper.SystemDeptMapper;
import com.machining.quote.mapper.SystemPostMapper;
import com.machining.quote.mapper.SystemUserMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SystemDeptService;
import com.machining.quote.service.SystemPostService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemOrganizationCrudTest {
    @Test
    void controllersExposeSecuredDepartmentAndPostEndpoints() throws Exception {
        assertThat(SystemDeptController.class).isNotNull();
        assertThat(SystemPostController.class).isNotNull();

        String deptController = Files.readString(
                Path.of("src/main/java/com/machining/quote/controller/SystemDeptController.java"),
                StandardCharsets.UTF_8);
        String postController = Files.readString(
                Path.of("src/main/java/com/machining/quote/controller/SystemPostController.java"),
                StandardCharsets.UTF_8);

        assertThat(deptController)
                .contains("@RequestMapping(\"/api/system/departments\")")
                .contains("@RequirePermission(\"system:dept:view\")")
                .contains("@RequirePermission(\"system:dept:manage\")");
        assertThat(postController)
                .contains("@RequestMapping(\"/api/system/posts\")")
                .contains("@RequirePermission(\"system:post:view\")")
                .contains("@RequirePermission(\"system:post:manage\")");
    }

    @Test
    void departmentServiceCreatesDepartmentAndRecordsAudit() {
        SystemDeptMapper deptMapper = mock(SystemDeptMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemDeptService service = deptService(deptMapper, mock(SystemUserMapper.class), auditService);

        when(deptMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            SystemDept dept = invocation.getArgument(0);
            dept.setId(10L);
            return 1;
        }).when(deptMapper).insert(any(SystemDept.class));

        SystemDeptSaveReq req = new SystemDeptSaveReq();
        req.setDeptCode(" market ");
        req.setDeptName("市场部");
        req.setParentId(0L);
        req.setSortOrder(20);
        req.setEnabled(true);

        SystemDeptDTO result = service.saveDept(req);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getDeptCode()).isEqualTo("MARKET");
        verify(deptMapper).insert(any(SystemDept.class));
        verify(auditService).record(eq("SYSTEM_DEPT_CREATE"), eq("system_dept"), eq(10L), isNull(), any());
    }

    @Test
    void deletingDepartmentRejectsExistingChildrenOrUsers() {
        SystemDeptMapper deptMapper = mock(SystemDeptMapper.class);
        SystemUserMapper userMapper = mock(SystemUserMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemDeptService service = deptService(deptMapper, userMapper, auditService);
        when(deptMapper.selectById(5L)).thenReturn(dept(5L, "MARKET", "市场部"));
        when(deptMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteDept(5L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("子部门");

        verify(deptMapper, never()).updateById(any(SystemDept.class));
        verify(userMapper, never()).selectCount(any(QueryWrapper.class));
        verify(auditService, never()).record(eq("SYSTEM_DEPT_DELETE"), eq("system_dept"), eq(5L), any(), isNull());
    }

    @Test
    void postServiceCreatesPostAndRejectsDeleteWhenUsersReferenceIt() {
        SystemPostMapper postMapper = mock(SystemPostMapper.class);
        SystemUserMapper userMapper = mock(SystemUserMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemPostService service = postService(postMapper, userMapper, auditService);

        when(postMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            SystemPost post = invocation.getArgument(0);
            post.setId(30L);
            return 1;
        }).when(postMapper).insert(any(SystemPost.class));

        SystemPostSaveReq req = new SystemPostSaveReq();
        req.setPostCode(" engineer ");
        req.setPostName("工程核价员");
        req.setSortOrder(10);
        req.setEnabled(true);

        SystemPostDTO result = service.savePost(req);

        assertThat(result.getId()).isEqualTo(30L);
        assertThat(result.getPostCode()).isEqualTo("ENGINEER");
        verify(auditService).record(eq("SYSTEM_POST_CREATE"), eq("system_post"), eq(30L), isNull(), any());

        when(postMapper.selectById(30L)).thenReturn(post(30L, "ENGINEER", "工程核价员"));
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        assertThatThrownBy(() -> service.deletePost(30L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户");
        verify(postMapper, never()).updateById(any(SystemPost.class));
    }

    @Test
    void organizationPermissionMigrationSeedsOrgMenuAndSuperAdminGrants() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V2__qpo_baseline_seed_data.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("system:org:view")
                .contains("system:dept:view")
                .contains("system:dept:manage")
                .contains("system:post:view")
                .contains("system:post:manage")
                .contains("SUPER_ADMIN")
                .contains("system_role_permission");
    }

    private SystemDeptService deptService(SystemDeptMapper deptMapper, SystemUserMapper userMapper, AuditService auditService) {
        SystemDeptService service = new SystemDeptService();
        ReflectionTestUtils.setField(service, "deptMapper", deptMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    private SystemPostService postService(SystemPostMapper postMapper, SystemUserMapper userMapper, AuditService auditService) {
        SystemPostService service = new SystemPostService();
        ReflectionTestUtils.setField(service, "postMapper", postMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    private SystemDept dept(Long id, String code, String name) {
        SystemDept dept = new SystemDept();
        dept.setId(id);
        dept.setDeptCode(code);
        dept.setDeptName(name);
        dept.setEnabled(true);
        dept.setDeleted(false);
        return dept;
    }

    private SystemPost post(Long id, String code, String name) {
        SystemPost post = new SystemPost();
        post.setId(id);
        post.setPostCode(code);
        post.setPostName(name);
        post.setEnabled(true);
        post.setDeleted(false);
        return post;
    }
}
