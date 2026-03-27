package com.ticketbooking.order.config;

import com.ticketbooking.common.constant.RedisKeyConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import redis.embedded.RedisServer;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BookingLuaScript 单元测试
 * 使用 EmbeddedRedis 测试 Lua 脚本的原子扣减 + 限购逻辑
 */
class BookingLuaScriptTest {

    private static final String CONCERT_ID = "concert123";
    private static final String USER_ID = "user001";
    private static final String GRADE_ID = "gradeA";
    private static final int EXPIRE_SECONDS = 86400;

    private RedisServer redisServer;
    private StringRedisTemplate redisTemplate;
    private BookingLuaScript bookingLuaScript;

    @BeforeEach
    void setUp() throws Exception {
        // 启动内嵌 Redis
        redisServer = new RedisServer(6389);
        redisServer.start();

        // 配置 RedisTemplate
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 6389);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(factory);

        // 初始化 Lua 脚本
        bookingLuaScript = new BookingLuaScript();
        bookingLuaScript.init();
    }

    @AfterEach
    void tearDown() {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @Test
    @DisplayName("成功扣减库存 - 第一次购买")
    void testExecute_success_firstPurchase() {
        // Given: 初始化库存和限购配置
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(Long.parseLong(CONCERT_ID));
        String limitKey = RedisKeyConstants.buildConcertLimitKey(Long.parseLong(CONCERT_ID));
        redisTemplate.opsForHash().put(stockHashKey, GRADE_ID, "100");
        redisTemplate.opsForValue().set(limitKey, "4");

        // When: 用户购买 2 张票
        Long result = executeScript(CONCERT_ID, USER_ID, "2", GRADE_ID);

        // Then: 返回成功
        assertThat(result).isEqualTo(1L);

        // 库存扣减
        String stock = (String) redisTemplate.opsForHash().get(stockHashKey, GRADE_ID);
        assertThat(stock).isEqualTo("98");

        // 用户购买记录增加
        String userPurchaseKey = RedisKeyConstants.buildUserConcertPurchaseKey(
                Long.parseLong(CONCERT_ID), Long.parseLong(USER_ID));
        String purchased = redisTemplate.opsForValue().get(userPurchaseKey);
        assertThat(purchased).isEqualTo("2");
    }

    @Test
    @DisplayName("成功扣减库存 - 第二次购买（累加）")
    void testExecute_success_secondPurchase() {
        // Given: 初始化库存、限购配置和已有购买记录
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(Long.parseLong(CONCERT_ID));
        String limitKey = RedisKeyConstants.buildConcertLimitKey(Long.parseLong(CONCERT_ID));
        String userPurchaseKey = RedisKeyConstants.buildUserConcertPurchaseKey(
                Long.parseLong(CONCERT_ID), Long.parseLong(USER_ID));

        redisTemplate.opsForHash().put(stockHashKey, GRADE_ID, "100");
        redisTemplate.opsForValue().set(limitKey, "4");
        redisTemplate.opsForValue().set(userPurchaseKey, "2");

        // When: 用户再购买 2 张票
        Long result = executeScript(CONCERT_ID, USER_ID, "2", GRADE_ID);

        // Then: 返回成功
        assertThat(result).isEqualTo(1L);

        // 库存扣减
        String stock = (String) redisTemplate.opsForHash().get(stockHashKey, GRADE_ID);
        assertThat(stock).isEqualTo("98");

        // 用户购买记录累加
        String purchased = redisTemplate.opsForValue().get(userPurchaseKey);
        assertThat(purchased).isEqualTo("4");
    }

    @Test
    @DisplayName("返回 -2: 票务不存在（库存 Hash field 不存在）")
    void testExecute_ticketNotExist() {
        // Given: 只有限购配置，没有库存
        String limitKey = RedisKeyConstants.buildConcertLimitKey(Long.parseLong(CONCERT_ID));
        redisTemplate.opsForValue().set(limitKey, "4");

        // When: 尝试购买
        Long result = executeScript(CONCERT_ID, USER_ID, "1", GRADE_ID);

        // Then: 返回 -2
        assertThat(result).isEqualTo(-2L);
    }

    @Test
    @DisplayName("返回 -3: 库存不足")
    void testExecute_insufficientStock() {
        // Given: 库存只有 5 张，限购 10 张
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(Long.parseLong(CONCERT_ID));
        String limitKey = RedisKeyConstants.buildConcertLimitKey(Long.parseLong(CONCERT_ID));

        redisTemplate.opsForHash().put(stockHashKey, GRADE_ID, "5");
        redisTemplate.opsForValue().set(limitKey, "10");

        // When: 尝试购买 6 张
        Long result = executeScript(CONCERT_ID, USER_ID, "6", GRADE_ID);

        // Then: 返回 -3
        assertThat(result).isEqualTo(-3L);

        // 库存未变化
        String stock = (String) redisTemplate.opsForHash().get(stockHashKey, GRADE_ID);
        assertThat(stock).isEqualTo("5");
    }

    @Test
    @DisplayName("返回 -4: 限购配置不存在")
    void testExecute_limitConfigNotExist() {
        // Given: 有库存但没有限购配置
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(Long.parseLong(CONCERT_ID));
        redisTemplate.opsForHash().put(stockHashKey, GRADE_ID, "100");

        // When: 尝试购买
        Long result = executeScript(CONCERT_ID, USER_ID, "1", GRADE_ID);

        // Then: 返回 -4
        assertThat(result).isEqualTo(-4L);
    }

    @Test
    @DisplayName("返回 -5: 超出限购 - 已购买数量 + 本次购买 > 限购")
    void testExecute_exceedLimit() {
        // Given: 限购 4 张，用户已买 3 张
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(Long.parseLong(CONCERT_ID));
        String limitKey = RedisKeyConstants.buildConcertLimitKey(Long.parseLong(CONCERT_ID));
        String userPurchaseKey = RedisKeyConstants.buildUserConcertPurchaseKey(
                Long.parseLong(CONCERT_ID), Long.parseLong(USER_ID));

        redisTemplate.opsForHash().put(stockHashKey, GRADE_ID, "100");
        redisTemplate.opsForValue().set(limitKey, "4");
        redisTemplate.opsForValue().set(userPurchaseKey, "3");

        // When: 尝试再买 2 张（3 + 2 = 5 > 4）
        Long result = executeScript(CONCERT_ID, USER_ID, "2", GRADE_ID);

        // Then: 返回 -5
        assertThat(result).isEqualTo(-5L);

        // 库存未变化
        String stock = (String) redisTemplate.opsForHash().get(stockHashKey, GRADE_ID);
        assertThat(stock).isEqualTo("100");

        // 用户购买记录未变化
        String purchased = redisTemplate.opsForValue().get(userPurchaseKey);
        assertThat(purchased).isEqualTo("3");
    }

    @Test
    @DisplayName("返回 -5: 超出限购 - 本次购买数量超过限购")
    void testExecute_exceedLimit_singlePurchase() {
        // Given: 限购 4 张
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(Long.parseLong(CONCERT_ID));
        String limitKey = RedisKeyConstants.buildConcertLimitKey(Long.parseLong(CONCERT_ID));

        redisTemplate.opsForHash().put(stockHashKey, GRADE_ID, "100");
        redisTemplate.opsForValue().set(limitKey, "4");

        // When: 尝试买 5 张
        Long result = executeScript(CONCERT_ID, USER_ID, "5", GRADE_ID);

        // Then: 返回 -5
        assertThat(result).isEqualTo(-5L);

        // 库存未变化
        String stock = (String) redisTemplate.opsForHash().get(stockHashKey, GRADE_ID);
        assertThat(stock).isEqualTo("100");
    }

    @Test
    @DisplayName("并发测试: 多个线程同时购买，库存正确扣减")
    void testExecute_concurrentPurchase() throws InterruptedException {
        // Given: 库存 10 张，限购 10 张
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(Long.parseLong(CONCERT_ID));
        String limitKey = RedisKeyConstants.buildConcertLimitKey(Long.parseLong(CONCERT_ID));

        redisTemplate.opsForHash().put(stockHashKey, GRADE_ID, "10");
        redisTemplate.opsForValue().set(limitKey, "10");

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 10 个线程同时购买 1 张票
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final String userId = String.valueOf(i);
            executor.submit(() -> {
                try {
                    Long result = executeScript(CONCERT_ID, userId, "1", GRADE_ID);
                    if (result == 1L) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: 所有购买都成功（每个用户独立限购）
        assertThat(successCount.get()).isEqualTo(10);

        // 库存扣减为 0
        String stock = (String) redisTemplate.opsForHash().get(stockHashKey, GRADE_ID);
        assertThat(stock).isEqualTo("0");
    }

    @Test
    @DisplayName("并发测试: 库存不足时，只有部分请求成功")
    void testExecute_concurrentPurchase_partialSuccess() throws InterruptedException {
        // Given: 库存 5 张，限购 10 张
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(Long.parseLong(CONCERT_ID));
        String limitKey = RedisKeyConstants.buildConcertLimitKey(Long.parseLong(CONCERT_ID));

        redisTemplate.opsForHash().put(stockHashKey, GRADE_ID, "5");
        redisTemplate.opsForValue().set(limitKey, "10");

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger stockInsufficientCount = new AtomicInteger(0);

        // When: 10 个线程同时购买 1 张票
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final String userId = String.valueOf(i);
            executor.submit(() -> {
                try {
                    Long result = executeScript(CONCERT_ID, userId, "1", GRADE_ID);
                    if (result == 1L) {
                        successCount.incrementAndGet();
                    } else if (result == -3L) {
                        stockInsufficientCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: 只有 5 个成功，5 个库存不足
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(stockInsufficientCount.get()).isEqualTo(5);

        // 库存扣减为 0
        String stock = (String) redisTemplate.opsForHash().get(stockHashKey, GRADE_ID);
        assertThat(stock).isEqualTo("0");
    }

    @Test
    @DisplayName("不同档位库存独立扣减")
    void testExecute_differentGrades() {
        // Given: 两个档位各有不同库存
        String stockHashKey = RedisKeyConstants.buildTicketStockHashKey(Long.parseLong(CONCERT_ID));
        String limitKey = RedisKeyConstants.buildConcertLimitKey(Long.parseLong(CONCERT_ID));

        redisTemplate.opsForHash().put(stockHashKey, "gradeA", "100");
        redisTemplate.opsForHash().put(stockHashKey, "gradeB", "50");
        redisTemplate.opsForValue().set(limitKey, "10");

        // When: 用户购买 gradeA
        Long resultA = executeScript(CONCERT_ID, USER_ID, "2", "gradeA");
        // 用户购买 gradeB
        Long resultB = executeScript(CONCERT_ID, USER_ID, "3", "gradeB");

        // Then: 都成功
        assertThat(resultA).isEqualTo(1L);
        assertThat(resultB).isEqualTo(1L);

        // 各档位库存独立扣减
        assertThat((String) redisTemplate.opsForHash().get(stockHashKey, "gradeA")).isEqualTo("98");
        assertThat((String) redisTemplate.opsForHash().get(stockHashKey, "gradeB")).isEqualTo("47");

        // 用户总购买数 = 5
        String userPurchaseKey = RedisKeyConstants.buildUserConcertPurchaseKey(
                Long.parseLong(CONCERT_ID), Long.parseLong(USER_ID));
        assertThat(redisTemplate.opsForValue().get(userPurchaseKey)).isEqualTo("5");
    }

    /**
     * 执行 Lua 脚本
     */
    private Long executeScript(String concertId, String userId, String quantity, String gradeId) {
        String stockHashKey = RedisKeyConstants.TICKET_STOCK_KEY + concertId;
        String userPurchaseKey = RedisKeyConstants.USER_CONCERT_PURCHASE_KEY + concertId + ":" + userId;
        String limitKey = RedisKeyConstants.CONCERT_LIMIT_KEY + concertId;

        DefaultRedisScript<Long> script = bookingLuaScript.getBookingScript();

        return redisTemplate.execute(
                script,
                List.of(stockHashKey, userPurchaseKey, limitKey),
                userId, quantity, gradeId, String.valueOf(EXPIRE_SECONDS)
        );
    }
}
