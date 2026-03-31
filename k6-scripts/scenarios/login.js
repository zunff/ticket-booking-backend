/**
 * 用户登录接口压测脚本
 * 测试目标：测试用户登录接口的性能和并发处理能力
 */

import http from 'k6';
import { check, sleep } from 'k6';
import { config, endpoints, getApiUrl } from '../config.js';
import { getTestUser } from '../lib/auth.js';

// 压测配置
export const options = {
    stages: config.stages.medium,
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        http_req_failed: ['rate<0.05'],
    },
};

/**
 * 压测主函数
 */
export default function () {
    const vuId = __VU;
    const iteration = __ITER;

    // 获取当前VU对应的测试用户
    const user = getTestUser(vuId, iteration);

    // 登录请求
    const url = getApiUrl(endpoints.login);
    const payload = JSON.stringify({
        username: user.username,
        password: user.password,
    });

    const params = {
        headers: { 'Content-Type': 'application/json' },
    };

    const response = http.post(url, payload, params);

    // 验证结果
    check(response, {
        '登录状态码为200': (r) => r.status === 200,
        '返回token': (r) => {
            const body = JSON.parse(r.body);
            return body.data && body.data.token !== undefined;
        },
        '响应时间小于500ms': (r) => r.timings.duration < 500,
    });

    // 模拟用户思考时间
    sleep(1);
}

/**
 * 清理函数
 */
export function teardown() {
    console.log('登录压测完成');
}
