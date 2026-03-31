/**
 * 抢票核心流程压测脚本
 * 测试目标：模拟真实抢票场景，测试高并发下的库存扣减和订单创建
 *
 * 核心测试点：
 * 1. Redis Lua 脚本原子性
 * 2. 限购逻辑
 * 3. Kafka 异步处理
 * 4. Sentinel 限流
 */

import http from 'k6';
import { check, sleep } from 'k6';
import { config, endpoints, getApiUrl } from '../config.js';
import { login, getAuthHeaders, getTestUser } from '../lib/auth.js';
import { randomInt } from '../lib/helpers.js';

// 压测配置 - 使用阶梯式加压
export const options = {
    stages: config.stages.stress,
    thresholds: {
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        // 抢票场景允许更高的错误率（库存不足、限流等）
        http_req_failed: ['rate<0.3'],
    },
};

// 测试数据配置
const CONCERT_ID = config.testData.concertId;
const GRADE_ID = config.testData.gradeId;

// 统计数据
let successCount = 0;
let stockInsufficientCount = 0;
let limitExceededCount = 0;
let rateLimitCount = 0;
let loginFailCount = 0;

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
        loginFailCount++;
        sleep(1);
        return;
    }

    // 构建抢票请求
    const url = getApiUrl(endpoints.book);
    const payload = JSON.stringify({
        concertId: CONCERT_ID,
        gradeId: GRADE_ID,
        quantity: 1,
    });

    const params = {
        headers: getAuthHeaders(auth.token),
    };

    // 发送抢票请求
    const response = http.post(url, payload, params);

    // 解析响应
    const body = JSON.parse(response.body);

    // 验证结果
    check(response, {
        '状态码为200': (r) => r.status === 200,
        '返回数据': (r) => {
            try {
                const b = JSON.parse(r.body);
                return b.code !== undefined;
            } catch {
                return false;
            }
        },
    });

    // 根据业务返回码统计
    if (response.status === 200 && body) {
        if (body.code === 200) {
            // 抢票成功
            successCount++;
            console.log(`VU ${vuId}: 抢票成功! 订单号: ${body.data || 'N/A'}`);
        } else if (body.code === 500) {
            // 业务错误，检查具体原因
            const message = body.message || '';
            if (message.includes('库存不足') || message.includes('-3')) {
                stockInsufficientCount++;
            } else if (message.includes('限购') || message.includes('-5')) {
                limitExceededCount++;
            }
        }
    } else if (response.status === 429) {
        // 触发限流
        rateLimitCount++;
        console.log(`VU ${vuId}: 触发限流，等待后重试`);
        sleep(5);
        return;
    }

    // 短暂等待，模拟用户操作间隔
    sleep(randomInt(1, 3));
}

/**
 * 清理函数：输出统计信息
 */
export function teardown() {
    console.log('\n========== 抢票压测结果 ==========');
    console.log(`抢票成功: ${successCount}`);
    console.log(`库存不足: ${stockInsufficientCount}`);
    console.log(`超出限购: ${limitExceededCount}`);
    console.log(`触发限流: ${rateLimitCount}`);
    console.log(`登录失败: ${loginFailCount}`);
    console.log('===================================\n');
}
