package com.ticketbooking.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private Long concertId;

    private Long gradeId;

    private Integer quantity;

    private Integer totalPrice;

    private Integer status;

    /**
     * 失败原因（当 status=FAILED 时记录）
     */
    private String failReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
