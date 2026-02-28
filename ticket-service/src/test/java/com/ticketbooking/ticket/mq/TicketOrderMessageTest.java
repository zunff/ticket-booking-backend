package com.ticketbooking.ticket.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;

public class TicketOrderMessageTest {
    
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        
        TicketOrderMessage msg = new TicketOrderMessage(
            "ORDER123", 
            1L, 
            1L, 
            1, 
            new BigDecimal("299.00")
        );
        
        System.out.println("Before serialization: userId=" + msg.getUserId());
        
        String json = mapper.writeValueAsString(msg);
        System.out.println("JSON: " + json);
        
        TicketOrderMessage deserialized = mapper.readValue(json, TicketOrderMessage.class);
        System.out.println("After deserialization: userId=" + deserialized.getUserId());
    }
}
