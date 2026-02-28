package com.ticketbooking.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.user.entity.User;

public interface UserService extends IService<User> {
    User findByUsername(String username);
    boolean validateUser(Long userId);
}
