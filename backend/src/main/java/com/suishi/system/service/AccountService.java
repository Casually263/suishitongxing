package com.suishi.system.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suishi.common.BusinessException;
import com.suishi.security.EmailCrypto;
import com.suishi.security.UserPrincipal;
import com.suishi.system.dto.ProfileRequest;
import com.suishi.system.dto.RegisterRequest;
import com.suishi.system.entity.Profile;
import com.suishi.system.entity.User;
import com.suishi.system.mapper.LogMapper;
import com.suishi.system.mapper.ProfileMapper;
import com.suishi.system.mapper.UserMapper;
import com.suishi.system.vo.ProfileView;
import com.suishi.system.vo.UserView;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;

@Service
public class AccountService {
    private final UserMapper users;
    private final ProfileMapper profiles;
    private final LogMapper logs;
    private final PasswordEncoder passwords;
    private final EmailCrypto email;
    private final ObjectMapper json;
    public AccountService(UserMapper users, ProfileMapper profiles, LogMapper logs, PasswordEncoder passwords, EmailCrypto email, ObjectMapper json) {
        this.users=users; this.profiles=profiles; this.logs=logs; this.passwords=passwords; this.email=email; this.json=json;
    }
    @Transactional
    public String register(RegisterRequest request) {
        requirePassword(request.password());
        if(email.normalize(request.email()).getBytes(StandardCharsets.UTF_8).length>254) throw invalid("邮箱不能超过254个UTF-8字节");
        User user=new User(); user.username=request.username(); user.usernameNormalized=request.username().toLowerCase(Locale.ROOT);
        user.emailEnc=email.encrypt(request.email()); user.emailLookup=email.lookup(request.email());
        user.passwordHash=passwords.encode(request.password()); user.roleType=request.cultureRole().name(); user.status="ACTIVE";
        user.authVersion=0; user.adultStatus=request.adultConfirmed(); user.emailVerified=false; user.termsVersion=request.termsVersion();
        users.insert(user); users.addRole(user.id, request.cultureRole()==RegisterRequest.CultureRole.GUIDE?1L:2L);
        Profile profile=new Profile(); profile.userId=user.id; profile.displayName=user.username; profile.country=null;
        profile.nativeLang=request.cultureRole()==RegisterRequest.CultureRole.GUIDE?"zh":"en";
        profile.learnLang=profile.nativeLang.equals("zh")?"en":"zh"; profile.langLevel="{\"zh\":0,\"en\":0}";
        profile.timezone="Asia/Shanghai"; profile.availSlots="[]"; profile.privacy="{\"showCountry\":false,\"showAvatar\":false}";
        profile.notificationPreferences="{\"termReminders\":false,\"partnerReminders\":false}"; profile.version=0;
        profiles.insert(profile); return user.id.toString();
    }
    public UserView user(Long userId) {
        User user=users.selectById(userId); if(user==null) throw missing();
        return UserView.of(user, users.roles(userId), users.permissions(userId));
    }
    public UserPrincipal principal(User user, String channel) { return new UserPrincipal(user.id,user.username,user.authVersion,channel,users.roles(user.id),users.permissions(user.id)); }
    public ProfileView profile(Long userId) {
        Profile p=profiles.forUser(userId); if(p==null) throw missing();
        return new ProfileView(p.displayName,p.country,p.nativeLang,p.learnLang,decode(p.langLevel,new TypeReference<>(){}),p.timezone,
                decode(p.availSlots,new TypeReference<>(){}),profiles.userTags(userId),decode(p.privacy,new TypeReference<>(){}),decode(p.notificationPreferences,new TypeReference<>(){}),p.version);
    }
    @Transactional
    public ProfileView updateProfile(Long userId, ProfileRequest request) {
        if(!ZoneId.getAvailableZoneIds().contains(request.timezone())) throw invalid("请选择有效的IANA时区");
        for(var slot:request.availSlots()) if(slot.start().compareTo(slot.end())>=0) throw invalid("可协作时段的结束时间须晚于开始时间；跨日请拆分");
        if(!Set.of("showCountry","showAvatar").containsAll(request.privacy().keySet()) || request.privacy().containsValue(null)) throw invalid("隐私设置不合法");
        if(!Set.of("termReminders","partnerReminders").containsAll(request.notificationPreferences().keySet()) || request.notificationPreferences().containsValue(null)) throw invalid("通知设置不合法");
        Set<String> tags=new LinkedHashSet<>(request.tagIds());
        for(String tag:tags) if(profiles.validTag(Long.valueOf(tag))!=1) throw invalid("兴趣标签不存在或已停用");
        Profile p=profiles.forUser(userId); if(p==null) throw missing();
        p.displayName=request.displayName().strip(); p.country=request.country(); p.nativeLang=request.nativeLang(); p.learnLang=request.learnLang();
        p.langLevel=encode(request.langLevel()); p.timezone=request.timezone(); p.availSlots=encode(request.availSlots());
        p.privacy=encode(request.privacy()); p.notificationPreferences=encode(request.notificationPreferences()); p.version=request.expectedVersion();
        if(profiles.updateOptimistic(p)!=1) throw new BusinessException(HttpStatus.CONFLICT,"VERSION_CONFLICT","档案已在其他页面更新，请刷新后重试");
        profiles.clearTags(userId); for(String tag:tags) profiles.addTag(userId,Long.valueOf(tag));
        logs.operation(IdWorker.getId(),userId,"PROFILE_UPDATE",userId); return profile(userId);
    }
    public List<Map<String,Object>> tags() {
        return profiles.tags().stream().map(row -> {
            var result=new LinkedHashMap<String,Object>(); result.put("id",row.get("id").toString()); result.put("code",row.get("code"));
            result.put("name",decode(row.get("name").toString(),new TypeReference<Map<String,String>>(){})); return (Map<String,Object>)result;
        }).toList();
    }
    public static void requirePassword(String password) {
        int bytes=password.getBytes(StandardCharsets.UTF_8).length;
        if(password.length()<12 || bytes>72) throw invalid("密码须至少12个字符且不超过72个UTF-8字节");
    }
    private String encode(Object value) { try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw invalid("档案格式不合法");} }
    private <T>T decode(String value,TypeReference<T> type) { try{return json.readValue(value,type);}catch(JsonProcessingException e){throw new IllegalStateException("Stored profile format invalid");} }
    private static BusinessException invalid(String message) { return new BusinessException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message); }
    private static BusinessException missing() { return new BusinessException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","账号档案不存在"); }
}
