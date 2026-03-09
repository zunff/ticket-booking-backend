package com.ticketbooking.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.enums.ErrorCode;
import com.ticketbooking.common.exception.BusinessException;
import com.ticketbooking.common.utils.JwtUtils;
import com.ticketbooking.common.utils.PasswordEncoder;
import com.ticketbooking.user.converter.UserConverter;
import com.ticketbooking.user.entity.User;
import com.ticketbooking.user.mapper.UserMapper;
import com.ticketbooking.user.service.UserService;
import com.ticketbooking.user.model.qo.LoginQO;
import com.ticketbooking.user.model.vo.LoginVO;
import com.ticketbooking.user.model.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final JwtUtils jwtUtils;
    private final UserConverter userConverter;
    
    @Override
    public User findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }
    
    @Override
    public boolean validateUser(Long userId) {
        return getById(userId) != null;
    }

    @Override
    public UserVO getUserVOById(Long id) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return userConverter.toVO(user);
    }

    @Override
    public UserVO registerAndReturnVO(User user) {
        User registeredUser = register(user);
        return userConverter.toVO(registeredUser);
    }
    
    @Override
    public LoginVO login(LoginQO qo) {
        User user = findByUsername(qo.getUsername());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_LOGIN_FAILED);
        }
        if (!PasswordEncoder.matches(qo.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_LOGIN_FAILED);
        }
        
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getIsAdmin());

        UserVO userVO = userConverter.toVO(user);

        return LoginVO.builder()
                .user(userVO)
                .token(token)
                .build();
    }

    @Override
    public User register(User user) {
        if (findByUsername(user.getUsername()) != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        user.setIsAdmin(false);
        user.setPassword(PasswordEncoder.encode(user.getPassword()));
        save(user);
        return user;
    }
}
