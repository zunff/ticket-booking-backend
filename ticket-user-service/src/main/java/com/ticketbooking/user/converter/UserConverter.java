package com.ticketbooking.user.converter;

import com.ticketbooking.user.entity.User;
import com.ticketbooking.user.model.vo.UserVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户实体与VO转换器
 */
@Component
public class UserConverter {

    public UserVO toVO(User user) {
        if (user == null) return null;
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    public List<UserVO> toVOList(List<User> users) {
        return users.stream().map(this::toVO).collect(Collectors.toList());
    }
}
