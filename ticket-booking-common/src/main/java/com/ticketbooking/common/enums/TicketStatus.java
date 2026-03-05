package com.ticketbooking.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TicketStatus {
    
    AVAILABLE("AVAILABLE", "可购买"),
    SOLD_OUT("SOLD_OUT", "已售罄"),
    SUSPENDED("SUSPENDED", "已停售"),
    OFF_SHELF("OFF_SHELF", "已下架"),
    ENDED("ENDED", "已结束");
    
    private final String code;
    private final String desc;
}
