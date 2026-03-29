package com.ticketbooking.stock.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ticketbooking.common.annotation.RequireAuth;
import com.ticketbooking.common.enums.Role;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.common.result.Result;
import com.ticketbooking.stock.converter.StockLogConverter;
import com.ticketbooking.stock.entity.StockLog;
import com.ticketbooking.stock.model.qo.StockLogQueryQO;
import com.ticketbooking.stock.model.vo.StockLogVO;
import com.ticketbooking.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class StockAdminController {

    private final StockService stockService;
    private final StockLogConverter stockLogConverter;

    /**
     * 获取库存日志（分页）
     */
    @GetMapping("/logs")
    @RequireAuth(Role.ADMIN)
    public Result<PageResult<StockLogVO>> getStockLogs(StockLogQueryQO qo) {
        IPage<StockLog> page = stockService.getStockLogsPage(qo);
        List<StockLogVO> voList = stockLogConverter.toVOList(page.getRecords());

        PageResult<StockLogVO> result = PageResult.of(
            voList,
            page.getTotal(),
            page.getCurrent(),
            page.getSize()
        );
        return Result.success(result);
    }

    /**
     * 根据演唱会和票档获取库存日志（保留兼容性）
     */
    @GetMapping("/logs/{concertId}/{gradeId}")
    @RequireAuth(Role.ADMIN)
    public Result<List<StockLogVO>> getStockLogsByConcertAndGrade(
            @PathVariable Long concertId,
            @PathVariable Long gradeId) {
        List<StockLog> logs = stockService.getStockLogs(concertId, gradeId);
        return Result.success(stockLogConverter.toVOList(logs));
    }

    @PostMapping("/adjust")
    @RequireAuth(Role.ADMIN)
    public Result<String> adjustStock(
            @RequestParam Long concertId,
            @RequestParam Long gradeId,
            @RequestParam Integer newStock,
            @RequestParam(required = false, defaultValue = "管理员调整") String remark) {
        stockService.adjustStock(concertId, gradeId, newStock, remark);
        return Result.success("库存调整成功");
    }
}
