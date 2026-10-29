package com.suishi.system.service;

import com.suishi.system.dto.RegisterRequest;
import com.suishi.system.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Component
public class BootstrapRunner implements ApplicationRunner {
    private final AccountService accounts;
    private final UserMapper users;
    private final Environment environment;
    @Value("${app.bootstrap.enabled}") private boolean enabled;
    @Value("${app.bootstrap.admin-username}") private String adminUsername;
    @Value("${app.bootstrap.admin-password}") private String adminPassword;
    @Value("${app.bootstrap.demo-enabled}") private boolean demoEnabled;
    @Value("${app.bootstrap.demo-password}") private String demoPassword;
    public BootstrapRunner(AccountService accounts,UserMapper users,Environment environment) { this.accounts=accounts;this.users=users;this.environment=environment; }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if(enabled) {
            if(!adminUsername.matches("[A-Za-z0-9_]{3,64}")) throw new IllegalStateException("Invalid bootstrap admin username");
            var existing=users.findByIdentifier(adminUsername.toLowerCase(Locale.ROOT),"");
            if(existing==null) {
                AccountService.requirePassword(adminPassword);
                String id=accounts.register(new RegisterRequest(adminUsername,adminUsername+"@demo.invalid",adminPassword,RegisterRequest.CultureRole.GUIDE,true,true,"2026-10"));
                users.addRole(Long.valueOf(id),3L);
            } else if(!users.roles(existing.id).contains("ADMIN")) throw new IllegalStateException("Bootstrap name belongs to a non-administrator; no privileges were granted");
        }
        if(demoEnabled) {
            if(!environment.acceptsProfiles(Profiles.of("dev")) || environment.acceptsProfiles(Profiles.of("prod"))) throw new IllegalStateException("Demo accounts are only permitted in dev");
            AccountService.requirePassword(demoPassword);
            seed("demo_guide",RegisterRequest.CultureRole.GUIDE); seed("demo_partner",RegisterRequest.CultureRole.PARTNER);
        }
    }
    private void seed(String username,RegisterRequest.CultureRole role) {
        if(users.findByIdentifier(username,"")==null) accounts.register(new RegisterRequest(username,username+"@demo.invalid",demoPassword,role,true,true,"2026-10"));
    }
}
