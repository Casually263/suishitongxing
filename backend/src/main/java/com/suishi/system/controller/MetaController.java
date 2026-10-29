package com.suishi.system.controller;

import com.suishi.common.Result;
import com.suishi.system.service.AccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
public class MetaController {
    private final AccountService accounts;
    public MetaController(AccountService accounts) { this.accounts=accounts; }
    @GetMapping("/api/common/meta") public Result<Map<String,Object>> meta() {
        return Result.ok(Map.of("languages",List.of("zh","en"),"interestTags",accounts.tags(),"termsVersion","2026-10",
                "features",Map.of("registration",true,"profile",true,"adminUsers",true,"stories",true,"aiCompanion",true,"wechatLogin",false,"emailVerification",false)));
    }
}
