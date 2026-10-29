package com.suishi.system.controller;

import com.suishi.common.Result;
import com.suishi.security.UserPrincipal;
import com.suishi.system.dto.ProfileRequest;
import com.suishi.system.service.AccountService;
import com.suishi.system.vo.ProfileView;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/portal/profile","/api/app/profile"})
public class ProfileController {
    private final AccountService accounts;
    public ProfileController(AccountService accounts) { this.accounts=accounts; }
    @GetMapping public Result<ProfileView> get(@AuthenticationPrincipal UserPrincipal principal) { return Result.ok(accounts.profile(principal.id())); }
    @PutMapping @PreAuthorize("hasAuthority('profile:write')")
    public Result<ProfileView> update(@AuthenticationPrincipal UserPrincipal principal,@Valid @RequestBody ProfileRequest body) { return Result.ok(accounts.updateProfile(principal.id(),body)); }
}
