package com.ticketbooking.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ticket_grade")
public class TicketGrade {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long concertId;
    
    private String gradeName;
    
    private Integer price;
    
    private Integer totalStock;
    
    private Integer isSelectedSeat;
    
    private LocalDateTime createdAt;
}
