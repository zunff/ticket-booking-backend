/**
 * 混合场景压测脚本
 * 测试目标：模拟真实用户行为流程，测试系统综合性能
 *
 * 使用方法：
 *   ./sh/pre-login.sh 500 http://192.168.249.231:9000
 *   k6 run k6-scripts/scenarios/mixed-flow.js --vus 100 --duration 3m \
 *     -e BASE_URL=http://192.168.249.231:9000 -e CONCERT_ID=2 -e GRADE_ID=6
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { config, endpoints, getApiUrl } from '../config.js';
import { getAuthHeaders } from '../lib/auth.js';
import { getAuth } from '../lib/token-cache.js';
import { randomInt, randomChoice } from '../lib/helpers.js';

// 压测配置
export const options = {
    stages: [
        { duration: '30s', target: 10 },   // 预热阶段：逐步增加到 10 VU
        { duration: '1m', target: 50 },    // 第一阶段：增加到 50 VU
        { duration: '2m', target: 100 },   // 第二阶段：增加到 100 VU
        { duration: '1m', target: 50 },    // 下降阶段
        { duration: '30s', target: 0 },    // 结束阶段
    ],
    thresholds: {
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        http_req_failed: ['rate<0.2'],
    },
    // 连接超时和请求超时设置
    noConnectionReuse: false,
    // 设置每个连接的最大请求数，避免长连接问题
    maxRedirects: 0,
};

// 测试数据
const CONCERT_ID = config.testData.concertId;
const GRADE_ID = config.testData.gradeId;
const concertIds = [CONCERT_ID];

// 用户行为权重配置
const BEHAVIOR_WEIGHTS = {
    browse_list: 30,      // 30% 浏览列表
    view_detail: 40,      // 40% 查看详情
    try_booking: 20,      // 20% 尝试抢票
    check_order: 10,      // 10% 查看订单
};

/**
 * 根据权重选择行为
 */
function selectBehavior() {
    const total = Object.values(BEHAVIOR_WEIGHTS).reduce((a, b) => a + b, 0);
    const rand = randomInt(1, total);

    let cumulative = 0;
    for (const [behavior, weight] of Object.entries(BEHAVIOR_WEIGHTS)) {
        cumulative += weight;
        if (rand <= cumulative) {
            return behavior;
        }
    }
    return 'browse_list';
}

// HTTP 请求通用配置
const HTTP_PARAMS = {
    timeout: '30s',
    throw: false,  // 不抛出异常，让 check 处理
};

/**
 * 浏览演唱会列表
 */
function browseConcertList() {
    const current = randomInt(1, 3);
    const size = randomInt(10, 20);
    const url = getApiUrl(endpoints.concerts) + '?current=' + current + '&size=' + size;

    const response = http.get(url, HTTP_PARAMS);

    check(response, {
        '浏览列表成功': (r) => r.status === 200,
    });

    sleep(randomInt(2, 5));
}

/**
 * 查看演唱会详情
 */
function viewConcertDetail(auth, concertId) {
    const url = getApiUrl(endpoints.concertDetail(concertId));
    const params = {
        ...HTTP_PARAMS,
        headers: getAuthHeaders(auth.token),
    };

    const response = http.get(url, params);

    check(response, {
        '查看详情成功': (r) => r.status === 200,
    });

    sleep(randomInt(3, 8));
}

/**
 * 尝试抢票
 */
function tryBooking(auth, concertId, gradeId) {
    const url = getApiUrl(endpoints.book);
    const payload = JSON.stringify({
        concertId: concertId,
        gradeId: gradeId,
        quantity: 1,
    });

    const params = {
        ...HTTP_PARAMS,
        headers: getAuthHeaders(auth.token),
    };

    const response = http.post(url, payload, params);

    check(response, {
        '抢票请求发送': (r) => r.status === 200 || r.status === 429,
    });

    sleep(randomInt(1, 3));
}

/**
 * 查看订单状态
 */
function checkOrderStatus(auth) {
    const url = getApiUrl(endpoints.userOrders(auth.userId)) + '?current=1&size=10';
    const params = {
        ...HTTP_PARAMS,
        headers: getAuthHeaders(auth.token),
    };

    const response = http.get(url, params);

    check(response, {
        '查看订单列表成功': (r) => r.status === 200,
    });

    sleep(randomInt(1, 2));
}

/**
 * 压测主函数
 */
export default function () {
    const vuId = __VU;
    const iteration = __ITER;

    // 首次迭代添加随机延迟，错开启动避免瞬间压垮服务器
    if (iteration === 0) {
        sleep(Math.random() * 3);
    }

    // 获取认证信息
    const auth = getAuth(vuId, iteration);

    if (!auth) {
        sleep(1);
        return;
    }

    // 选择行为
    const behavior = selectBehavior();

    switch (behavior) {
        case 'browse_list':
            browseConcertList();
            break;

        case 'view_detail':
            viewConcertDetail(auth, randomChoice(concertIds) || CONCERT_ID);
            break;

        case 'try_booking':
            tryBooking(auth, randomChoice(concertIds) || CONCERT_ID, GRADE_ID);
            break;

        case 'check_order':
            checkOrderStatus(auth);
            break;
    }
}
