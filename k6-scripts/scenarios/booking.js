/**
 * 抢票核心流程压测脚本
 * 测试目标：模拟真实抢票场景，测试高并发下的库存扣减和订单创建
 *
 * 使用方法：
 *   # 1. 预登录生成Token缓存
 *   ./sh/pre-login.sh 500 http://192.168.249.231:9000
 *
 *   # 2. 运行压测
 *   k6 run k6-scripts/scenarios/booking.js --vus 200 --duration 3m \
 *     -e BASE_URL=http://192.168.249.231:9000 -e CONCERT_ID=2 -e GRADE_ID=6
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { config, endpoints, getApiUrl } from '../config.js';
import { getAuthHeaders } from '../lib/auth.js';
import { getAuth, getTokenCache } from '../lib/token-cache.js';
import { randomInt } from '../lib/helpers.js';

// 压测配置
export const options = {
    scenarios: {
        booking_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 1000 },
                { duration: '30s', target: 3000 },
                { duration: '30s', target: 5000 },
                { duration: '30s', target: 8000 },
                { duration: '30s', target: 10000 },
                { duration: '1m', target: 10000 },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<2000', 'p(99)<5000'],
        http_req_failed: ['rate<0.1'],
    },
};

// 测试数据配置
const CONCERT_ID = config.testData.concertId;
const GRADE_ID = config.testData.gradeId;
const tokenCache = getTokenCache();

// 打印测试参数
console.log('========================================');
console.log('压测参数配置:');
console.log('  BASE_URL:', config.baseUrl);
console.log('  CONCERT_ID:', CONCERT_ID);
console.log('  GRADE_ID:', GRADE_ID);
console.log('  TOKEN_CACHE:', tokenCache.length > 0 ? tokenCache.length + '个' : '无缓存');
console.log('  STAGES:', JSON.stringify(options.scenarios.booking_test.stages));
console.log('========================================');

// 自定义指标
const bookingSuccess = new Counter('booking_success');
const bookingLimitExceeded = new Counter('booking_limit_exceeded');
const bookingStockInsufficient = new Counter('booking_stock_insufficient');
const bookingRateLimit = new Counter('booking_rate_limit');
const bookingLoginFail = new Counter('booking_login_fail');
const bookingError = new Counter('booking_error');

/**
 * 压测主函数
 */
export default function () {
    const vuId = __VU;
    const iteration = __ITER;

    // 首次迭代添加随机延迟，错开启动
    if (iteration === 0) {
        sleep(Math.random() * 2);
    }

    // 获取认证信息
    const auth = getAuth(vuId, iteration);

    if (!auth) {
        bookingLoginFail.add(1);
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
        responseCallback: http.expectedStatuses(200, 400, 429),
    };

    // 发送抢票请求
    const response = http.post(url, payload, params);

    // 检查网络错误
    if (response.error || response.status === 0) {
        bookingError.add(1);
        return;
    }

    // 解析响应
    let body = null;
    try {
        body = JSON.parse(response.body);
    } catch (e) {
        bookingError.add(1);
        return;
    }

    // 检查响应体格式
    if (!body || typeof body.code === 'undefined') {
        bookingError.add(1);
        return;
    }

    // 根据业务返回码统计
    const code = Number(body.code);
    if (code === 200) {
        bookingSuccess.add(1);
        console.log('VU ' + vuId + ': 抢票成功! 订单号: ' + (body.data || 'N/A'));
    } else if (code === 4001) {
        bookingLimitExceeded.add(1);
    } else if (code === 4002 || (body.message && body.message.includes('库存不足'))) {
        bookingStockInsufficient.add(1);
    } else if (response.status === 429) {
        bookingRateLimit.add(1);
    } else if (response.status === 401) {
        console.log('VU ' + vuId + ': Token可能已过期');
        bookingError.add(1);
    } else {
        bookingError.add(1);
        if (iteration < 3) {
            console.log('VU ' + vuId + ': 业务码 ' + code + ', ' + body.message);
        }
    }

}
