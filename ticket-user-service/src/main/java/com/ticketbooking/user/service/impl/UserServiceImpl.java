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
import com.ticketbooking.user.model.qo.ChangePasswordQO;
import com.ticketbooking.user.model.qo.LoginQO;
import com.ticketbooking.user.model.qo.RegisterQO;
import com.ticketbooking.user.model.qo.UpdateProfileQO;
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
     * 更新用户个人信息
     */
    @Override
    public UserVO updateProfile(Long userId, UpdateProfileQO qo) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 更新允许修改的字段
        if (qo.getNickname() != null) {
            user.setNickname(qo.getNickname());
        }
        if (qo.getEmail() != null) {
            user.setEmail(qo.getEmail());
        }
        if (qo.getPhone() != null) {
            user.setPhone(qo.getPhone());
        }

        updateById(user);
        log.info("用户个人信息更新: userId={}", userId);

        // 清除缓存
        evictUserCache(userId);

        return userConverter.toVO(user);
    }

    /**
     * 修改密码
     */
    @Override
    public void changePassword(Long userId, ChangePasswordQO qo) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 验证旧密码
        if (!PasswordEncoder.matches(qo.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }

        // 设置新密码
        user.setPassword(PasswordEncoder.encode(qo.getNewPassword()));
        updateById(user);
        log.info("用户密码修改: userId={}", userId);

        // 清除缓存
        evictUserCache(userId);
    }

    /**
     * 注册并自动登录（返回token）
     */
    @Override
    public LoginVO registerAndLogin(RegisterQO qo) {
        if (findByUsername(qo.getUsername()) != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(qo.getUsername());
        user.setPassword(PasswordEncoder.encode(qo.getPassword()));
        user.setNickname(qo.getNickname() != null && !qo.getNickname().isEmpty()
            ? qo.getNickname() : qo.getUsername());
        user.setEmail(qo.getEmail());
        user.setPhone(qo.getPhone());
        user.setIsAdmin(false);

        save(user);
        log.info("用户注册成功: username={}", qo.getUsername());

        // 生成token
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getIsAdmin());
        UserVO userVO = userConverter.toVO(user);

        return LoginVO.builder()
                .user(userVO)
                .token(token)
                .build();
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

    /**
     * 清除用户缓存
     */
    private void evictUserCache(Long userId) {
        String cacheKey = String.valueOf(userId);
        String redisKey = RedisKeyConstants.buildUserInfoKey(userId);
        cacheService.evict(cacheKey, redisKey);
        log.info("用户缓存已清除: userId={}", userId);
    }
}
