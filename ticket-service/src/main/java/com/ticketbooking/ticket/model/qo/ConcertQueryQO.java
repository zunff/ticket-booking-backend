package com.ticketbooking.ticket.model.qo;

import com.ticketbooking.common.model.qo.PageQO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 演唱会分页查询QO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConcertQueryQO extends PageQO {

    /**
     * 演唱会名称（模糊查询）
     */
    private String name;

    /**
     * 动态状态筛选（基于当前时间计算）
     * 0-已关闭（数据库status=0）
     * 1-开售中（当前时间在售票时间内）
     * 2-即将开售（当前时间<开始售票时间）
     * 3-已结束（当前时间>=结束售票时间）
     * null-显示所有非关闭的演唱会
     */
    private Integer timeStatus;
}
