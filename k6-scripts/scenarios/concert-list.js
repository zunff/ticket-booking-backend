/**
 * 演唱会列表查询压测脚本
 * 测试目标：测试演唱会列表分页查询接口的性能
 */

import http from 'k6';
import { check, sleep } from 'k6';
import { config, endpoints, getApiUrl } from '../config.js';
import { randomInt } from '../lib/helpers.js';

// 压测配置
export const options = {
    stages: config.stages.medium,
    thresholds: {
        http_req_duration: ['p(95)<300', 'p(99)<500'],
        http_req_failed: ['rate<0.01'],
    },
};

/**
 * 压测主函数
 */
export default function () {
    // 随机分页参数
    const current = randomInt(1, 5);
    const size = randomInt(10, 20);

    // 构建查询URL
    const url = `${getApiUrl(endpoints.concerts)}?current=${current}&size=${size}`;

    const params = {
        headers: { 'Content-Type': 'application/json' },
    };

    const response = http.get(url, params);

    // 验证结果
    check(response, {
        '状态码为200': (r) => r.status === 200,
        '返回分页数据': (r) => {
            const body = JSON.parse(r.body);
            return body.data && body.data.records !== undefined;
        },
        '响应时间小于300ms': (r) => r.timings.duration < 300,
    });

    // 模拟用户浏览时间
    sleep(randomInt(1, 3));
}
