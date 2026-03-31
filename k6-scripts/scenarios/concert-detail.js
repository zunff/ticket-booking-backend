/**
 * 演唱会详情查询压测脚本
 * 测试目标：测试需要认证的详情查询接口性能，包含限流测试
 */

import http from 'k6';
import { check, sleep } from 'k6';
import { config, endpoints, getApiUrl } from '../config.js';
import { login, getAuthHeaders, getTestUser } from '../lib/auth.js';
import { randomInt, randomChoice } from '../lib/helpers.js';

// 压测配置
export const options = {
    stages: config.stages.medium,
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        http_req_failed: ['rate<0.1'], // 考虑限流导致的失败
    },
};

// 演唱会ID列表
const concertIds = [1, 2, 3];

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
        console.log(`VU ${vuId}: 登录失败`);
        sleep(1);
        return;
    }

    // 选择演唱会ID
    const concertId = randomChoice(concertIds) || config.testData.concertId;

    // 使用认证请求查询详情
    const url = getApiUrl(endpoints.concertDetail(concertId));
    const params = {
        headers: getAuthHeaders(auth.token),
    };

    const response = http.get(url, params);

    // 验证结果
    check(response, {
        '状态码为200': (r) => r.status === 200,
        '返回演唱会详情': (r) => {
            const body = JSON.parse(r.body);
            return body.data && body.data.id !== undefined;
        },
        '包含票档信息': (r) => {
            const body = JSON.parse(r.body);
            return body.data && body.data.grades !== undefined;
        },
    });

    // 检查限流响应
    if (response.status === 429) {
        console.log(`VU ${vuId}: 触发限流`);
        sleep(5);
        return;
    }

    // 模拟用户查看详情时间
    sleep(randomInt(2, 5));
}

/**
 * 清理函数
 */
export function teardown() {
    console.log('演唱会详情查询压测完成');
}
