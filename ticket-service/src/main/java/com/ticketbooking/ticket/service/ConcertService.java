package com.ticketbooking.ticket.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketbooking.common.model.PageResult;
import com.ticketbooking.ticket.entity.Concert;
import com.ticketbooking.ticket.model.qo.ConcertQueryQO;
import com.ticketbooking.ticket.model.vo.ConcertDetailWithStockVO;
import com.ticketbooking.ticket.model.vo.ConcertVO;

import java.util.List;

/**
 * 演唱会服务接口
 */
public interface ConcertService extends IService<Concert> {

    /**
     * 创建演唱会
     */
    Concert createConcert(Concert concert);

    /**
     * 更新演唱会
     */
    Concert updateConcert(Concert concert);

    /**
     * 获取所有演唱会
     */
    List<Concert> getAllConcerts();

    /**
     * 获取转换演唱会列表为VO列表
     */
    List<ConcertVO> getConcertVOList(List<Concert> concerts);

    /**
     * 分页查询演唱会列表
     *
     * @param qo 查询条件
     * @return 分页结果（VO）
     */
    PageResult<ConcertVO> getConcerts(ConcertQueryQO qo);

    /**
     * 获取演唱会详情（包含库存信息）
     *
     * @param id 演唱会ID
     * @return 演唱会详情VO
     */
    ConcertDetailWithStockVO getConcertDetailById(Long id);

    /**
     * 获取演唱会详情（包含库存信息和用户购买数量）
     *
     * @param id 演唱会ID
     * @param userId 用户ID（可为null，未登录时不查询购买数量）
     * @return 演唱会详情VO
     */
    ConcertDetailWithStockVO getConcertDetailById(Long id, Long userId);

    /**
     * 根据ID获取演唱会实体
     */
    Concert getConcertById(Long id);

    /**
     * 删除演唱会
     */
    void deleteConcert(Long concertId);
}
