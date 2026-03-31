/**
 * k6 压测配置文件
 * 根据实际部署环境修改相应配置
 */

export const config = {
    // 网关地址，所有API通过网关访问
    baseUrl: __ENV.BASE_URL || 'http://localhost:9000',

    // 预置测试用户配置（由 init-test-users.sql 插入）
    testUsers: {
        // 用户名前缀
        userPrefix: 'k6_test_',
        // 统一密码
        userPassword: 'testpass123',
        // 用户数量（与SQL中插入的用户数一致）
        userCount: parseInt(__ENV.USER_COUNT || '500'),
    },

    // 测试数据配置
    testData: {
        // 演唱会ID
        concertId: parseInt(__ENV.CONCERT_ID || '1'),
        // 票档ID
        gradeId: parseInt(__ENV.GRADE_ID || '1'),
    },

    // 压测阶段配置
    stages: {
        // 小规模测试
        small: [
            { duration: '30s', target: 50 },
            { duration: '1m', target: 100 },
            { duration: '30s', target: 0 },
        ],
        // 中等规模测试
        medium: [
            { duration: '1m', target: 200 },
            { duration: '2m', target: 500 },
            { duration: '1m', target: 1000 },
            { duration: '30s', target: 0 },
        ],
        // 大规模测试
        large: [
            { duration: '2m', target: 500 },
            { duration: '3m', target: 2000 },
            { duration: '2m', target: 5000 },
            { duration: '1m', target: 0 },
        ],
        // 极限测试（阶梯加压）
        stress: [
            { duration: '1m', target: 100 },
            { duration: '30s', target: 100 },
            { duration: '1m', target: 500 },
            { duration: '30s', target: 500 },
            { duration: '1m', target: 1000 },
            { duration: '30s', target: 1000 },
            { duration: '1m', target: 2000 },
            { duration: '30s', target: 2000 },
            { duration: '1m', target: 3000 },
            { duration: '30s', target: 0 },
        ],
    },

    // 性能阈值配置
    thresholds: {
        // 95%的请求响应时间应低于此值
        responseTime95: 500, // ms
        // 99%的请求响应时间应低于此值
        responseTime99: 1000, // ms
        // 错误率阈值
        errorRate: 0.05, // 5%
    },
};

/**
 * API 端点定义
 */
export const endpoints = {
    // 用户服务
    login: '/api/users/login',

    // 演唱会服务
    concerts: '/api/ticket/concerts',
    concertDetail: (id) => `/api/ticket/concerts/${id}`,

    // 订单服务
    book: '/api/order/book',
    orderDetail: (orderNo) => `/api/order/${orderNo}`,
    userOrders: (userId) => `/api/order/user/${userId}`,

    // 库存服务
    stocks: (concertId) => `/api/stock/${concertId}`,
};

/**
 * 获取完整的API URL
 */
export function getApiUrl(endpoint) {
    return `${config.baseUrl}${endpoint}`;
}

/**
 * 生成测试用户名
 * @param {number} index - 用户索引 (1-500)
 * @returns {string} 用户名
 */
export function generateUsername(index) {
    return `${config.testUsers.userPrefix}${String(index).padStart(3, '0')}`;
}
