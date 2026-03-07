package com.ticketbooking.stock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock")
public class Stock {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long concertId;
    
    private Long gradeId;
    
    private Integer availableStock;
    
    private Integer version;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
