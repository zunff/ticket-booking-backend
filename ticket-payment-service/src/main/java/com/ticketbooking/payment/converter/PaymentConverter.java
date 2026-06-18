package com.ticketbooking.payment.converter;

import com.ticketbooking.common.enums.PayChannel;
import com.ticketbooking.common.enums.PayMode;
import com.ticketbooking.common.enums.PaymentStatus;
import com.ticketbooking.payment.entity.PaymentRecord;
import com.ticketbooking.common.model.dto.PayResponseDTO;
import com.ticketbooking.common.model.dto.RefundResultDTO;
import com.ticketbooking.payment.model.vo.PaymentRecordVO;
import com.ticketbooking.payment.model.vo.PaymentVO;
import com.ticketbooking.payment.model.vo.RefundVO;
import org.springframework.stereotype.Component;

@Component
public class PaymentConverter {

    public PaymentRecordVO toDTO(PaymentRecord record) {
        if (record == null) return null;
        PaymentRecordVO dto = new PaymentRecordVO();
        dto.setId(record.getId());
        dto.setPaymentNo(record.getPaymentNo());
        dto.setOutTradeNo(record.getOutTradeNo());
        dto.setChannel(PayChannel.of(record.getChannel()).getDesc());
        dto.setPayMode(PayMode.of(record.getPayMode()).getDesc());
        dto.setAmount(record.getAmount());
        dto.setPaidAmount(record.getPaidAmount());
        dto.setStatus(PaymentStatus.of(record.getStatus()).getDesc());
        dto.setChannelTradeNo(record.getChannelTradeNo());
        dto.setSubject(record.getSubject());
        dto.setPayTime(record.getPayTime());
        dto.setCreateTime(record.getCreateTime());
        return dto;
    }

    public PaymentVO toVO(PayResponseDTO response) {
        if (response == null) return null;
        PaymentVO vo = new PaymentVO();
        vo.setPaymentNo(response.getPaymentNo());
        vo.setChannelTradeNo(response.getChannelTradeNo());
        vo.setPayMode(response.getPayMode().getDesc());
        vo.setPayUrl(response.getPayUrl());
        vo.setPayParams(response.getPayParams());
        return vo;
    }

    public RefundVO toVO(RefundResultDTO result) {
        if (result == null) return null;
        RefundVO vo = new RefundVO();
        vo.setSuccess(result.isSuccess());
        vo.setRefundNo(result.getRefundNo());
        vo.setChannelRefundNo(result.getChannelRefundNo());
        vo.setRefundAmount(result.getRefundAmount());
        vo.setRefundTime(result.getRefundTime());
        return vo;
    }
}
