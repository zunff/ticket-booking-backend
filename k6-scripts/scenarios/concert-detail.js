/**
 * 演唱会详情查询压测脚本
 * 测试目标：测试需要认证的详情查询接口性能
 *
 * 使用方法：
 *   ./sh/pre-login.sh 500 http://192.168.249.231:9000
 *   k6 run k6-scripts/scenarios/concert-detail.js --vus 100 --duration 2m \
 *     -e BASE_URL=http://192.168.249.231:9000
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
        { duration: '30s', target: 30 },   // 预热阶段
        { duration: '1m', target: 100 },   // 高负载
        { duration: '1m', target: 200 },   // 峰值
        { duration: '30s', target: 0 },    // 结束
    ],
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        http_req_failed: ['rate<0.1'],
    },
};

// HTTP 请求通用配置
const HTTP_PARAMS = {
    timeout: '30s',
    throw: false,
};

// 演唱会ID列表
const concertIds = [1, 2, 3];

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

    // 获取认证信息
    const auth = getAuth(vuId, iteration);

    if (!auth) {
        sleep(1);
        return;
    }

    // 选择演唱会ID
    const concertId = randomChoice(concertIds) || config.testData.concertId;

    // 查询详情
    const url = getApiUrl(endpoints.concertDetail(concertId));
    const params = {
        ...HTTP_PARAMS,
        headers: getAuthHeaders(auth.token),
    };

    const response = http.get(url, params);

    // 验证结果
    check(response, {
        '状态码为200': (r) => r.status === 200,
        '返回演唱会详情': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.id !== undefined;
            } catch {
                return false;
            }
        },
    });

    // 检查限流响应
    if (response.status === 429) {
        sleep(5);
        return;
    }

    // 模拟用户查看详情时间
    sleep(randomInt(2, 5));
}
