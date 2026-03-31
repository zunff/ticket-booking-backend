/**
 * 混合场景压测脚本
 * 测试目标：模拟真实用户行为流程，测试系统综合性能
 *
 * 用户行为流程：
 * 1. 登录系统
 * 2. 浏览演唱会列表
 * 3. 查看演唱会详情
 * 4. 尝试抢票
 * 5. 查看订单状态
 */

import http from 'k6';
import { check, sleep } from 'k6';
import { config, endpoints, getApiUrl } from '../config.js';
import { login, getAuthHeaders, getTestUser } from '../lib/auth.js';
import { randomInt, randomChoice } from '../lib/helpers.js';

// 压测配置
export const options = {
    scenarios: {
        // 混合场景：模拟真实用户行为
        realistic_flow: {
            executor: 'ramping-vus',
            stages: [
                { duration: '1m', target: 50 },
                { duration: '3m', target: 200 },
                { duration: '2m', target: 500 },
                { duration: '1m', target: 200 },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        http_req_failed: ['rate<0.2'],
    },
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

/**
 * 浏览演唱会列表
 */
function browseConcertList() {
    const current = randomInt(1, 3);
    const size = randomInt(10, 20);
    const url = `${getApiUrl(endpoints.concerts)}?current=${current}&size=${size}`;

    const response = http.get(url);

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
        headers: getAuthHeaders(auth.token),
    };

    const response = http.post(url, payload, params);

    check(response, {
        '抢票请求发送': (r) => r.status === 200 || r.status === 429,
    });

    if (response.status === 200) {
        const body = JSON.parse(response.body);
        if (body.code === 200) {
            return body.data;
        }
    }

    sleep(randomInt(1, 3));
    return null;
}

/**
 * 查看订单状态
 */
function checkOrderStatus(auth) {
    const url = `${getApiUrl(endpoints.userOrders(auth.userId))}?current=1&size=10`;
    const params = {
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

    // 获取测试用户并登录
    const user = getTestUser(vuId, iteration);
    const auth = login(user.username, user.password);

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

/**
 * 清理函数
 */
export function teardown() {
    console.log('混合场景压测完成');
}
