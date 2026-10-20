package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.SystemPostDTO;
import com.machining.quote.dto.SystemPostSaveReq;
import com.machining.quote.service.SystemPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/posts")
public class SystemPostController {
    @Autowired
    private SystemPostService systemPostService;

    @GetMapping
    @RequirePermission("system:post:view")
    public List<SystemPostDTO> listPosts() {
        return systemPostService.listPosts();
    }

    @GetMapping("/options")
    @RequirePermission("system:user:view")
    public List<SystemPostDTO> listOptions() {
        return systemPostService.listPostOptions();
    }

    @PostMapping
    @RequirePermission("system:post:manage")
    public Map<String, Object> createPost(@RequestBody SystemPostSaveReq req) {
        return Map.of("success", true, "message", "Post created", "data", systemPostService.savePost(req));
    }

    @PutMapping("/{id}")
    @RequirePermission("system:post:manage")
    public Map<String, Object> updatePost(@PathVariable Long id, @RequestBody SystemPostSaveReq req) {
        req.setId(id);
        return Map.of("success", true, "message", "Post updated", "data", systemPostService.savePost(req));
    }

    @PutMapping("/{id}/status")
    @RequirePermission("system:post:manage")
    public Map<String, Object> updateStatus(@PathVariable Long id, @RequestBody SystemPostSaveReq req) {
        return Map.of("success", true, "message", "Post status updated", "data", systemPostService.updateStatus(id, req.getEnabled()));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("system:post:manage")
    public Map<String, Object> deletePost(@PathVariable Long id) {
        systemPostService.deletePost(id);
        return Map.of("success", true, "message", "Post deleted");
    }
}
