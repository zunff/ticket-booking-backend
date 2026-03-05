package com.ticketbooking.order.model.qo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookTicketQO {
    
    @NotNull(message = "票务ID不能为空")
    private Long ticketId;
    
    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity = 1;
}
