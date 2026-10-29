package com.suishi.system.controller;

import com.suishi.common.Result;
import com.suishi.security.UserPrincipal;
import com.suishi.system.mapper.UserMapper;
import com.suishi.system.service.AdminService;
import com.suishi.system.vo.UserView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Validated
public class AdminController {
    private final AdminService admin;
    private final UserMapper users;
    public AdminController(AdminService admin,UserMapper users) { this.admin=admin;this.users=users; }
    @GetMapping("/overview") @PreAuthorize("hasAuthority('admin:access')")
    public Result<Map<String,Object>> overview() { return Result.ok(Map.of("registeredUsers",users.countUsers(),"generatedAt",Instant.now(),"source","DATABASE","stage",1)); }
    @GetMapping("/users") @PreAuthorize("hasAuthority('user:read')")
    public Result<AdminService.Page> users(@RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return Result.ok(admin.page(page,size)); }
    public record StatusRequest(@NotBlank @Pattern(regexp="ACTIVE|DISABLED") String status,@NotNull @Min(0) Integer expectedAuthVersion) {}
    @PutMapping("/users/{id}/status") @PreAuthorize("hasAuthority('user:write')")
    public Result<UserView> status(@AuthenticationPrincipal UserPrincipal actor,@PathVariable Long id,@Valid @RequestBody StatusRequest body) {
        return Result.ok(admin.status(actor.id(),id,body.status(),body.expectedAuthVersion()));
    }
}
