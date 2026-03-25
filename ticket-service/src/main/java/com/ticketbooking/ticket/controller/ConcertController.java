package com.ticketbooking.ticket.controller;

import com.ticketbooking.common.annotation.UserRateLimit;
import com.ticketbooking.common.context.UserContext;
import com.ticketbooking.common.context.UserInfo;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.ticket.model.qo.ConcertQueryQO;
import com.ticketbooking.ticket.model.vo.ConcertDetailWithStockVO;
import com.ticketbooking.ticket.model.vo.ConcertVO;
import com.ticketbooking.ticket.service.ConcertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演唱会控制器
 */
@RestController
@RequestMapping("/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertService concertService;

    /**
     * 分页查询演唱会列表
     *
     * @param qo 查询条件（包含分页、名称筛选、状态筛选）
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<ConcertVO>> getConcerts(ConcertQueryQO qo) {
        PageResult<ConcertVO> result = concertService.getConcerts(qo);
        return Result.success(result);
    }

    /**
     * 获取演唱会详情（包含库存信息和用户购买数量）
     *
     * @param id 演唱会ID
     * @return 演唱会详情
     */
    @UserRateLimit
    @GetMapping("/{id}")
    public Result<ConcertDetailWithStockVO> getConcertDetail(@PathVariable Long id) {
        // 获取当前登录用户ID
        UserInfo userInfo = UserContext.getUserInfo();
        Long userId = userInfo != null ? userInfo.getUserId() : null;

        ConcertDetailWithStockVO result = concertService.getConcertDetailById(id, userId);
        return Result.success(result);
    }
}
