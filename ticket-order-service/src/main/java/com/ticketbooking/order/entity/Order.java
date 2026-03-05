package com.ticketbooking.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String orderNo;
    
    private Long userId;
    
    private Long ticketId;
    
    private Integer quantity;
    
    private BigDecimal totalPrice;
    
    private String status;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}
