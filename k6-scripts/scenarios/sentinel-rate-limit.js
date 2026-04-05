/**
 * Sentinel 限流测试脚本
 * 
 * 用于验证 Sentinel 限流配置的有效性
 * 测试各种接口在不同 QPS 下的限流效果
 * 
 * 运行方式：
 * k6 run k6-scripts/scenarios/sentinel-rate-limit.js
 * 
 * 自定义网关地址：
 * BASE_URL=http://localhost:9000 k6 run k6-scripts/scenarios/sentinel-rate-limit.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ==================== 自定义指标 ====================

// HTTP 429 状态码比例（被限流的请求比例）
const rate429 = new Rate('rate_429');

// 响应时间趋势
const responseTime = new Trend('response_time');

// 被限流的请求计数器
const blockedRequests = new Counter('blocked_requests');

// ==================== 测试配置 ====================

export const options = {
    scenarios: {
        // 场景1: 登录接口限流测试（目标: 500 QPS）
        login_rate_limit: {
            executor: 'ramping-arrival-rate',  // 使用阶梯式到达率执行器
            exec: 'testLoginRateLimit',        // 执行的测试函数
            startRate: 100,                     // 起始请求率：100 请求/秒
            timeUnit: '1s',                     // 时间单位：1秒
            preAllocatedVUs: 50,                // 预分配虚拟用户数
            maxVUs: 200,                        // 最大虚拟用户数
            stages: [
                { target: 400, duration: '30s' },   // 第一阶段：增加到 400 QPS，持续30秒
                { target: 600, duration: '30s' },   // 第二阶段：增加到 600 QPS（超过限流阈值），持续30秒
                { target: 800, duration: '30s' },   // 第三阶段：增加到 800 QPS（继续超限），持续30秒
                { target: 0, duration: '10s' }      // 第四阶段：降到 0，持续10秒
            ]
        },
        
        // 场景2: 注册接口限流测试（目标: 100 QPS）
        register_rate_limit: {
            executor: 'ramping-arrival-rate',
            exec: 'testRegisterRateLimit',
            startRate: 20,
            timeUnit: '1s',
            preAllocatedVUs: 20,
            maxVUs: 100,
            stages: [
                { target: 80, duration: '30s' },    // 第一阶段：接近限流阈值
                { target: 120, duration: '30s' },   // 第二阶段：超过限流阈值
                { target: 150, duration: '30s' },   // 第三阶段：继续超限
                { target: 0, duration: '10s' }
            ]
        },
        
        // 场景3: 演唱会列表接口限流测试（目标: 1000 QPS）
        concerts_rate_limit: {
            executor: 'ramping-arrival-rate',
            exec: 'testConcertsRateLimit',
            startRate: 200,
            timeUnit: '1s',
            preAllocatedVUs: 50,
            maxVUs: 300,
            stages: [
                { target: 800, duration: '30s' },   // 第一阶段：接近限流阈值
                { target: 1200, duration: '30s' },  // 第二阶段：超过限流阈值
                { target: 1500, duration: '30s' },  // 第三阶段：继续超限
                { target: 0, duration: '10s' }
            ]
        },
        
        // 场景4: 抢票接口限流测试（目标: 5000 QPS）
        book_ticket_rate_limit: {
            executor: 'ramping-arrival-rate',
            exec: 'testBookTicketRateLimit',
            startRate: 1000,
            timeUnit: '1s',
            preAllocatedVUs: 100,
            maxVUs: 500,
            stages: [
                { target: 4000, duration: '30s' },  // 第一阶段：接近限流阈值
                { target: 5500, duration: '30s' },  // 第二阶段：超过限流阈值
                { target: 7000, duration: '30s' },  // 第三阶段：继续超限
                { target: 0, duration: '10s' }
            ]
        },
        
        // 场景5: 用户级限流测试（目标: 单用户 20 QPS）
        user_rate_limit: {
            executor: 'ramping-arrival-rate',
            exec: 'testUserRateLimit',
            startRate: 5,
            timeUnit: '1s',
            preAllocatedVUs: 10,
            maxVUs: 50,
            stages: [
                { target: 15, duration: '30s' },    // 第一阶段：接近限流阈值
                { target: 25, duration: '30s' },    // 第二阶段：超过限流阈值
                { target: 35, duration: '30s' },    // 第三阶段：继续超限
                { target: 0, duration: '10s' }
            ]
        }
    },
    
    // 阈值配置
    thresholds: {
        // P95 响应时间应小于 2000ms
        http_req_duration: ['p(95)<2000'],
        // HTTP 429 比例应大于 10%（表示限流生效）
        rate_429: ['rate>0.1']
    }
};

// ==================== 全局配置 ====================

// 基础 URL，可通过环境变量 BASE_URL 覆盖
const BASE_URL = __ENV.BASE_URL || 'http://localhost:9000';

// 网关 API 地址
const GATEWAY_URL = `${BASE_URL}/api`;

// 测试用户列表
const testUsers = [
    { username: 'testuser1', password: 'Test123456' },
    { username: 'testuser2', password: 'Test123456' },
    { username: 'testuser3', password: 'Test123456' }
];

// ==================== 测试函数 ====================

/**
 * 测试登录接口限流
 * 目标 QPS: 500
 */
export function testLoginRateLimit() {
    // 随机选择一个测试用户
    const user = testUsers[Math.floor(Math.random() * testUsers.length)];
    
    // 构造登录请求体
    const payload = JSON.stringify({
        username: user.username,
        password: user.password
    });

    // 请求头配置
    const params = {
        headers: {
            'Content-Type': 'application/json'
        }
    };

    // 发送登录请求
    const res = http.post(`${GATEWAY_URL}/users/login`, payload, params);
    
    // 验证响应
    check(res, {
        'login status is 200 or 429': (r) => r.status === 200 || r.status === 429,
        'login response time < 1s': (r) => r.timings.duration < 1000
    });

    // 记录 429 状态码（被限流）
    const is429 = res.status === 429;
    rate429.add(is429);
    responseTime.add(res.timings.duration);
    
    if (is429) {
        blockedRequests.add(1);
        console.log(`Login rate limited: ${res.status}`);
    }

    // 短暂休眠，避免请求过于密集
    sleep(0.1);
}

/**
 * 测试注册接口限流
 * 目标 QPS: 100
 */
export function testRegisterRateLimit() {
    const timestamp = Date.now();
    
    // 构造注册请求体（生成唯一的用户名、邮箱、手机号）
    const payload = JSON.stringify({
        username: `user_${timestamp}_${Math.random().toString(36).substring(7)}`,
        password: 'Test123456',
        email: `user_${timestamp}@test.com`,
        phone: `138${Math.floor(Math.random() * 1000000000).toString().padStart(9, '0')}`
    });

    // 请求头配置
    const params = {
        headers: {
            'Content-Type': 'application/json'
        }
    };

    // 发送注册请求
    const res = http.post(`${GATEWAY_URL}/users/register`, payload, params);
    
    // 验证响应
    check(res, {
        'register status is 200 or 429': (r) => r.status === 200 || r.status === 201 || r.status === 429,
        'register response time < 1s': (r) => r.timings.duration < 1000
    });

    // 记录 429 状态码
    const is429 = res.status === 429;
    rate429.add(is429);
    responseTime.add(res.timings.duration);
    
    if (is429) {
        blockedRequests.add(1);
        console.log(`Register rate limited: ${res.status}`);
    }

    sleep(0.2);
}

/**
 * 测试演唱会列表接口限流
 * 目标 QPS: 1000
 */
export function testConcertsRateLimit() {
    // 发送演唱会列表请求
    const res = http.get(`${GATEWAY_URL}/ticket/concerts?page=1&size=10`);
    
    // 验证响应
    check(res, {
        'concerts status is 200 or 429': (r) => r.status === 200 || r.status === 429,
        'concerts response time < 1s': (r) => r.timings.duration < 1000
    });

    // 记录 429 状态码
    const is429 = res.status === 429;
    rate429.add(is429);
    responseTime.add(res.timings.duration);
    
    if (is429) {
        blockedRequests.add(1);
        console.log(`Concerts rate limited: ${res.status}`);
    }

    sleep(0.05);
}

/**
 * 测试抢票接口限流
 * 目标 QPS: 5000
 */
export function testBookTicketRateLimit() {
    // 随机生成演唱会ID、票档ID、用户ID
    const concertId = Math.floor(Math.random() * 100) + 1;
    const gradeId = Math.floor(Math.random() * 10) + 1;
    const userId = Math.floor(Math.random() * 1000) + 1;
    
    // 构造抢票请求体
    const payload = JSON.stringify({
        concertId: concertId,
        gradeId: gradeId,
        userId: userId,
        quantity: 1
    });

    // 请求头配置（包含用户ID）
    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-User-Id': userId.toString()
        }
    };

    // 发送抢票请求
    const res = http.post(`${GATEWAY_URL}/order/book`, payload, params);
    
    // 验证响应（允许 400 状态码，因为可能存在业务校验失败）
    check(res, {
        'book ticket status is 200/201/400/429': (r) => 
            r.status === 200 || r.status === 201 || r.status === 400 || r.status === 429,
        'book ticket response time < 2s': (r) => r.timings.duration < 2000
    });

    // 记录 429 状态码
    const is429 = res.status === 429;
    rate429.add(is429);
    responseTime.add(res.timings.duration);
    
    if (is429) {
        blockedRequests.add(1);
        console.log(`Book ticket rate limited: ${res.status}`);
    }

    sleep(0.02);
}

/**
 * 测试用户级限流（单用户维度）
 * 目标: 单用户 20 QPS
 * 使用 @UserRateLimit 注解实现的限流
 */
export function testUserRateLimit() {
    const concertId = Math.floor(Math.random() * 100) + 1;
    const userId = 1;  // 使用固定用户ID，测试单用户限流
    
    // 请求头配置（包含用户ID）
    const params = {
        headers: {
            'X-User-Id': userId.toString()
        }
    };

    // 发送演唱会详情请求
    const res = http.get(`${GATEWAY_URL}/ticket/concerts/${concertId}`, params);
    
    // 验证响应
    check(res, {
        'user rate limit status is 200 or 429': (r) => r.status === 200 || r.status === 429,
        'user rate limit response time < 1s': (r) => r.timings.duration < 1000
    });

    // 记录 429 状态码
    const is429 = res.status === 429;
    rate429.add(is429);
    responseTime.add(res.timings.duration);
    
    if (is429) {
        blockedRequests.add(1);
        console.log(`User rate limited for concert detail: ${res.status}`);
    }

    sleep(0.05);
}

// ==================== 测试报告生成 ====================

/**
 * 生成测试报告
 * 输出 HTML 和 JSON 格式的报告
 */
export function handleSummary(data) {
    return {
        'k6-scripts/reports/sentinel-rate-limit-report.html': htmlReport(data),
        'k6-scripts/reports/sentinel-rate-limit-summary.json': JSON.stringify(data, null, 2)
    };
}

/**
 * 生成 HTML 格式的测试报告
 */
function htmlReport(data) {
    return `
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Sentinel 限流测试报告</title>
    <style>
        body { 
            font-family: Arial, sans-serif; 
            margin: 20px; 
            background: #f5f5f5; 
        }
        .container { 
            max-width: 1200px; 
            margin: 0 auto; 
            background: white; 
            padding: 20px; 
            border-radius: 8px; 
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        h1 { 
            color: #333; 
            border-bottom: 2px solid #4CAF50; 
            padding-bottom: 10px; 
        }
        h2 { 
            color: #555; 
            margin-top: 30px; 
        }
        .metric { 
            background: #f9f9f9; 
            padding: 15px; 
            margin: 10px 0; 
            border-left: 4px solid #4CAF50; 
        }
        .metric-name { 
            font-weight: bold; 
            color: #333; 
        }
        .metric-value { 
            font-size: 24px; 
            color: #4CAF50; 
            margin-top: 5px; 
        }
        .threshold-pass { color: #4CAF50; }
        .threshold-fail { color: #f44336; }
        table { 
            width: 100%; 
            border-collapse: collapse; 
            margin-top: 20px; 
        }
        th, td { 
            border: 1px solid #ddd; 
            padding: 12px; 
            text-align: left; 
        }
        th { 
            background-color: #4CAF50; 
            color: white; 
        }
        tr:nth-child(even) { 
            background-color: #f9f9f9; 
        }
        .scenario { 
            margin: 20px 0; 
            padding: 15px; 
            background: #e8f5e9; 
            border-radius: 4px; 
        }
        .success { color: #4CAF50; font-weight: bold; }
        .fail { color: #f44336; font-weight: bold; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🔒 Sentinel 限流测试报告</h1>
        
        <h2>📊 测试摘要</h2>
        <div class="metric">
            <div class="metric-name">总请求数</div>
            <div class="metric-value">${data.metrics.http_reqs.values.count}</div>
        </div>
        <div class="metric">
            <div class="metric-name">被限流请求数 (HTTP 429)</div>
            <div class="metric-value">${data.metrics.blocked_requests ? data.metrics.blocked_requests.values.count : 0}</div>
        </div>
        <div class="metric">
            <div class="metric-name">限流效果</div>
            <div class="metric-value">${((data.metrics.rate_429.values.rate) * 100).toFixed(2)}%</div>
        </div>
        
        <h2>⏱️ 响应时间</h2>
        <table>
            <tr>
                <th>指标</th>
                <th>值</th>
            </tr>
            <tr>
                <td>平均响应时间</td>
                <td>${data.metrics.http_req_duration.values.avg.toFixed(2)}ms</td>
            </tr>
            <tr>
                <td>P95 响应时间</td>
                <td>${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms</td>
            </tr>
            <tr>
                <td>最大响应时间</td>
                <td>${data.metrics.http_req_duration.values.max.toFixed(2)}ms</td>
            </tr>
        </table>
        
        <h2>✅ 阈值验证</h2>
        <table>
            <tr>
                <th>阈值</th>
                <th>状态</th>
            </tr>
            <tr>
                <td>HTTP 429 比例 > 10%（表示限流生效）</td>
                <td class="${data.metrics.rate_429.values.rate > 0.1 ? 'threshold-pass' : 'threshold-fail'}">
                    ${data.metrics.rate_429.values.rate > 0.1 ? '✅ 通过' : '❌ 失败'}
                </td>
            </tr>
            <tr>
                <td>P95 响应时间 < 2000ms</td>
                <td class="${data.metrics.http_req_duration.values['p(95)'] < 2000 ? 'threshold-pass' : 'threshold-fail'}">
                    ${data.metrics.http_req_duration.values['p(95)'] < 2000 ? '✅ 通过' : '❌ 失败'}
                </td>
            </tr>
        </table>
        
        <h2>🧪 测试场景</h2>
        <div class="scenario">
            <h3>1. 登录接口限流测试</h3>
            <p><strong>目标 QPS:</strong> 500</p>
            <p><strong>测试内容:</strong> 测试 /login 接口的限流效果</p>
        </div>
        <div class="scenario">
            <h3>2. 注册接口限流测试</h3>
            <p><strong>目标 QPS:</strong> 100</p>
            <p><strong>测试内容:</strong> 测试 /register 接口的限流效果</p>
        </div>
        <div class="scenario">
            <h3>3. 演唱会列表接口限流测试</h3>
            <p><strong>目标 QPS:</strong> 1000</p>
            <p><strong>测试内容:</strong> 测试 /concerts 接口的限流效果</p>
        </div>
        <div class="scenario">
            <h3>4. 抢票接口限流测试</h3>
            <p><strong>目标 QPS:</strong> 5000</p>
            <p><strong>测试内容:</strong> 测试 /order/book 接口的限流效果</p>
        </div>
        <div class="scenario">
            <h3>5. 用户级限流测试</h3>
            <p><strong>目标 QPS:</strong> 单用户 20 QPS</p>
            <p><strong>测试内容:</strong> 测试基于用户维度的限流效果</p>
        </div>
        
        <h2>📝 说明</h2>
        <ul>
            <li><strong>限流效果 > 10%</strong>：表示限流配置有效，超限请求被正确拦截</li>
            <li><strong>HTTP 429</strong>：表示请求被限流，返回"Too Many Requests"状态码</li>
            <li><strong>P95 响应时间</strong>：95% 的请求响应时间应低于此值</li>
        </ul>
    </div>
</body>
</html>
    `;
}
