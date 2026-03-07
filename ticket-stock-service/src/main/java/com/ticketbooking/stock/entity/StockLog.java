package com.ticketbooking.stock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_log")
public class StockLog {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long concertId;
    
    private Long gradeId;
    
    private String orderNo;
    
    private Integer changeAmount;
    
    private Integer beforeStock;
    
    private Integer afterStock;
    
    private String operationType;
    
    private String remark;
    
    private LocalDateTime createTime;
}
