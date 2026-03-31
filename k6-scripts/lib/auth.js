/**
 * 认证工具函数
 */

import http from 'k6/http';
import { check } from 'k6';
import { config, endpoints, getApiUrl, generateUsername } from '../config.js';

/**
 * 用户登录获取JWT Token
 * @param {string} username - 用户名
 * @param {string} password - 密码
 * @returns {object|null} 包含token和userId的对象，失败返回null
 */
export function login(username, password) {
    const url = getApiUrl(endpoints.login);
    const payload = JSON.stringify({
        username: username,
        password: password,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const response = http.post(url, payload, params);

    const success = check(response, {
        'login successful': (r) => r.status === 200,
        'has token': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.token !== undefined;
            } catch (e) {
                return false;
            }
        },
    });

    if (success) {
        const body = JSON.parse(response.body);
        return {
            token: body.data.token,
            userId: body.data.user.id,
            username: username,
        };
    }

    return null;
}

/**
 * 获取带认证的请求头
 * @param {string} token - JWT Token
 * @returns {object} 请求头对象
 */
export function getAuthHeaders(token) {
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
    };
}

/**
 * 根据VU ID获取对应的测试用户
 * @param {number} vuId - 虚拟用户ID
 * @param {number} iteration - 迭代次数
 * @returns {object} 用户信息 {username, password}
 */
export function getTestUser(vuId, iteration = 0) {
    const userIndex = ((vuId - 1) + iteration) % config.testUsers.userCount + 1;
    return {
        username: generateUsername(userIndex),
        password: config.testUsers.userPassword,
    };
}
