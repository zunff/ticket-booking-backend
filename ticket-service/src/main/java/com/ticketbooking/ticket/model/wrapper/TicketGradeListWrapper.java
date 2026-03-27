package com.ticketbooking.ticket.model.wrapper;

import com.ticketbooking.ticket.entity.TicketGrade;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 票价档位列表包装类
 * 用于多级缓存的 List 序列化
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketGradeListWrapper implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<TicketGrade> grades;

    public List<TicketGrade> getGrades() {
        return grades != null ? grades : new ArrayList<>();
    }
}
