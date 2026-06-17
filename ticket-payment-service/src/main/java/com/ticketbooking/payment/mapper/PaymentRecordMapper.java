package com.ticketbooking.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketbooking.payment.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
}
