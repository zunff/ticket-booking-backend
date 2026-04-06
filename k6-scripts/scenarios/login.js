/**
 * 用户登录接口压测脚本
 * 测试目标：测试用户登录接口的性能和并发处理能力
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { config, endpoints, getApiUrl } from '../config.js';
import { getTestUser } from '../lib/auth.js';

// 压测配置
export const options = {
    stages: [
        { duration: '30s', target: 50 },   // 预热阶段
        { duration: '1m', target: 200 },   // 高负载
        { duration: '30s', target: 0 },    // 结束
    ],
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        http_req_failed: ['rate<0.05'],
    },
};

// HTTP 请求通用配置
const HTTP_PARAMS = {
    headers: { 'Content-Type': 'application/json' },
    timeout: '30s',
    throw: false,
};

/**
 * 压测主函数
 */
export default function () {
    const vuId = __VU;
    const iteration = __ITER;

    // 首次迭代添加随机延迟，错开启动
    if (iteration === 0) {
        sleep(Math.random() * 3);
    }

    // 获取当前VU对应的测试用户
    const user = getTestUser(vuId, iteration);

    // 登录请求
    const url = getApiUrl(endpoints.login);
    const payload = JSON.stringify({
        username: user.username,
        password: user.password,
    });

    const response = http.post(url, payload, HTTP_PARAMS);

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
