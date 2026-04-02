package com.ticketbooking.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketbooking.common.cache.MultiLevelCacheService;
import com.ticketbooking.common.constant.CacheConstant;
import com.ticketbooking.common.constant.RedisExpireConstants;
import com.ticketbooking.common.constant.RedisKeyConstants;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final JwtUtils jwtUtils;
    private final UserConverter userConverter;
    private final MultiLevelCacheService cacheService;
    
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
        String cacheKey = String.valueOf(id);
        String redisKey = RedisKeyConstants.buildUserInfoKey(id);

        return cacheService.get(
                cacheKey,
                UserVO.class,
                redisKey,
                RedisExpireConstants.USER_INFO_EXPIRE_SECONDS,
                () -> {
                    User user = getById(id);
                    if (user == null) {
                        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                    }
                    return userConverter.toVO(user);
                }
        );
    }

    /**
     * 更新用户信息并清除缓存
     */
    @Override
    public UserVO updateUserAndReturnVO(User user) {
        // 检查用户是否存在
        User existing = getById(user.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 保留不可修改的字段
        user.setPassword(existing.getPassword());
        user.setIsAdmin(existing.getIsAdmin());

        // 更新数据库
        updateById(user);
        log.info("用户信息更新: userId={}", user.getId());

        // 清除缓存
        String cacheKey = String.valueOf(user.getId());
        String redisKey = RedisKeyConstants.buildUserInfoKey(user.getId());
        cacheService.evict(cacheKey, redisKey);
        log.info("用户缓存已清除: userId={}", user.getId());

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
