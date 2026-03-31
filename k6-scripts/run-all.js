/**
 * 综合压测脚本 - 运行所有场景
 * 使用场景：全面测试系统各接口性能
 *
 * 使用方法：
 *   k6 run run-all.js
 *   k6 run --env BASE_URL=http://your-server:9000 run-all.js
 */

import http from 'k6';
import { check, sleep } from 'k6';
import { config, endpoints, getApiUrl } from './config.js';
import { login, getAuthHeaders, getTestUser } from './lib/auth.js';
import { randomInt, randomChoice } from './lib/helpers.js';

// 压测配置 - 综合测试
export const options = {
    scenarios: {
        // 场景1: 登录接口压测
        login_test: {
            executor: 'ramping-vus',
            exec: 'loginTest',
            stages: [
                { duration: '30s', target: 50 },
                { duration: '1m', target: 100 },
                { duration: '30s', target: 0 },
            ],
            startTime: '0s',
        },

        // 场景2: 演唱会列表查询
        concert_list_test: {
            executor: 'ramping-vus',
            exec: 'concertListTest',
            stages: [
                { duration: '30s', target: 30 },
                { duration: '1m', target: 100 },
                { duration: '30s', target: 0 },
            ],
            startTime: '2m',
        },

        // 场景3: 演唱会详情查询
        concert_detail_test: {
            executor: 'ramping-vus',
            exec: 'concertDetailTest',
            stages: [
                { duration: '30s', target: 20 },
                { duration: '1m', target: 80 },
                { duration: '30s', target: 0 },
            ],
            startTime: '4m',
        },

        // 场景4: 抢票压测（核心）
        booking_test: {
            executor: 'ramping-vus',
            exec: 'bookingTest',
            stages: [
                { duration: '1m', target: 50 },
                { duration: '2m', target: 200 },
                { duration: '2m', target: 500 },
                { duration: '1m', target: 200 },
                { duration: '30s', target: 0 },
            ],
            startTime: '6m',
        },

        // 场景5: 混合流程
        mixed_test: {
            executor: 'ramping-vus',
            exec: 'mixedTest',
            stages: [
                { duration: '1m', target: 30 },
                { duration: '2m', target: 100 },
                { duration: '1m', target: 0 },
            ],
            startTime: '12m',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        http_req_failed: ['rate<0.2'],
    },
};

const concertIds = [config.testData.concertId];
const gradeIds = [config.testData.gradeId];

/**
 * 场景1: 登录测试
 */
export function loginTest() {
    const vuId = __VU;
    const iteration = __ITER;
    const user = getTestUser(vuId, iteration);

    const url = getApiUrl(endpoints.login);
    const payload = JSON.stringify({
        username: user.username,
        password: user.password,
    });

    const response = http.post(url, payload, {
        headers: { 'Content-Type': 'application/json' },
    });

    check(response, {
        '登录成功': (r) => r.status === 200,
    });

    sleep(1);
}

/**
 * 场景2: 演唱会列表测试
 */
export function concertListTest() {
    const current = randomInt(1, 5);
    const size = randomInt(10, 20);
    const url = `${getApiUrl(endpoints.concerts)}?current=${current}&size=${size}`;

    const response = http.get(url);

    check(response, {
        '列表查询成功': (r) => r.status === 200,
    });

    sleep(randomInt(1, 2));
}

/**
 * 场景3: 演唱会详情测试
 */
export function concertDetailTest() {
    const vuId = __VU;
    const iteration = __ITER;
    const user = getTestUser(vuId, iteration);
    const auth = login(user.username, user.password);

    if (!auth) {
        sleep(1);
        return;
    }

    const concertId = randomChoice(concertIds);
    const url = getApiUrl(endpoints.concertDetail(concertId));
    const response = http.get(url, {
        headers: getAuthHeaders(auth.token),
    });

    check(response, {
        '详情查询成功': (r) => r.status === 200,
    });

    sleep(randomInt(2, 4));
}

/**
 * 场景4: 抢票测试
 */
export function bookingTest() {
    const vuId = __VU;
    const iteration = __ITER;
    const user = getTestUser(vuId, iteration);
    const auth = login(user.username, user.password);

    if (!auth) {
        sleep(1);
        return;
    }

    const concertId = randomChoice(concertIds);
    const gradeId = randomChoice(gradeIds);

    const url = getApiUrl(endpoints.book);
    const payload = JSON.stringify({
        concertId: concertId,
        gradeId: gradeId,
        quantity: 1,
    });

    const response = http.post(url, payload, {
        headers: getAuthHeaders(auth.token),
    });

    check(response, {
        '抢票请求发送': (r) => r.status === 200 || r.status === 429,
    });

    sleep(randomInt(1, 3));
}

/**
 * 场景5: 混合流程测试
 */
export function mixedTest() {
    const vuId = __VU;
    const iteration = __ITER;
    const user = getTestUser(vuId, iteration);
    const auth = login(user.username, user.password);

    if (!auth) {
        sleep(1);
        return;
    }

    const concertId = randomChoice(concertIds);
    const gradeId = randomChoice(gradeIds);

    // 随机执行一个操作
    const action = randomInt(1, 4);

    switch (action) {
        case 1:
            // 浏览列表
            http.get(`${getApiUrl(endpoints.concerts)}?current=1&size=10`);
            break;
        case 2:
            // 查看详情
            http.get(getApiUrl(endpoints.concertDetail(concertId)), {
                headers: getAuthHeaders(auth.token),
            });
            break;
        case 3:
            // 抢票
            http.post(getApiUrl(endpoints.book), JSON.stringify({
                concertId: concertId,
                gradeId: gradeId,
                quantity: 1,
            }), {
                headers: getAuthHeaders(auth.token),
            });
            break;
        case 4:
            // 查看订单
            http.get(`${getApiUrl(endpoints.userOrders(auth.userId))}?current=1&size=5`, {
                headers: getAuthHeaders(auth.token),
            });
            break;
    }

    sleep(randomInt(1, 3));
}

/**
 * 清理函数
 */
export function teardown() {
    console.log('\n========================================');
    console.log('   票务系统综合压测 - 完成');
    console.log('========================================\n');
}
