package com.suishi.system.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.suishi.common.BusinessException;
import com.suishi.system.entity.User;
import com.suishi.system.mapper.LogMapper;
import com.suishi.system.mapper.UserMapper;
import com.suishi.system.vo.UserView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AdminService {
    public record Page(List<UserView> items,int page,int size,long total) {}
    private final UserMapper users;
    private final AccountService accounts;
    private final LogMapper logs;
    public AdminService(UserMapper users,AccountService accounts,LogMapper logs) { this.users=users;this.accounts=accounts;this.logs=logs; }
    public Page page(int page,int size) { return new Page(users.page(size,(page-1)*size).stream().map(user->accounts.user(user.id)).toList(),page,size,users.countUsers()); }
    @Transactional
    public UserView status(Long actor,Long id,String status,int expectedVersion) {
        users.lockAdminRole();
        User user=users.selectById(id); if(user==null) throw new BusinessException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","用户不存在");
        if("DISABLED".equals(status) && (actor.equals(id) || (users.roles(id).contains("ADMIN") && users.activeAdmins()<=1)))
            throw new BusinessException(HttpStatus.CONFLICT,"ADMIN_REQUIRED","不能停用自己或最后一个可用管理员");
        if(users.updateStatus(id,status,expectedVersion)!=1) throw new BusinessException(HttpStatus.CONFLICT,"VERSION_CONFLICT","用户状态已更新，请刷新");
        logs.operation(IdWorker.getId(),actor,"USER_STATUS_"+status,id); return accounts.user(id);
    }
}
