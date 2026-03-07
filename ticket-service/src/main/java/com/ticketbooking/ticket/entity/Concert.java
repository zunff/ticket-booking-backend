package com.ticketbooking.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("concerts")
public class Concert {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String name;
    
    private String venue;
    
    private LocalDateTime showTime;
    
    private LocalDateTime startSaleTime;
    
    private LocalDateTime endSaleTime;
    
    private Integer status;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
