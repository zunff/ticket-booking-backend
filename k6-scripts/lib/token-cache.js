/**
 * Token 缓存模块 - 共享给所有压测脚本使用
 *
 * 使用方法：
 *   import { getTokenCache, getAuth } from '../lib/token-cache.js';
 *
 *   const tokenCache = getTokenCache();
 *   const auth = getAuth(vuId, iteration, tokenCache);
 */

import { SharedArray } from 'k6/data';
import { login, getTestUser } from './auth.js';

// 加载 token 缓存
const tokenCache = new SharedArray('tokens', function () {
    const content = open(__ENV.PWD + '/k6-scripts/.token-cache.json');
    if (!content) return [];
    try {
        const data = JSON.parse(content);
        return Array.isArray(data) ? data : [];
    } catch (e) {
        return [];
    }
});

const hasCache = tokenCache.length > 0;

// 日志只在第一个 VU 初始化时打印一次
if (hasCache && __VU === 0) {
    console.log('已加载 ' + tokenCache.length + ' 个缓存Token');
}

/**
 * 获取 token 缓存数组
 */
export function getTokenCache() {
    return tokenCache;
}

/**
 * 检查是否有缓存
 */
export function hasTokenCache() {
    return hasCache;
}

/**
 * 获取认证信息（优先使用缓存，fallback 到登录）
 */
export function getAuth(vuId, iteration) {
    if (hasCache) {
        const index = ((vuId - 1) + iteration) % tokenCache.length;
        return tokenCache[index];
    }

    // fallback: 登录获取
    const user = getTestUser(vuId, iteration);
    return login(user.username, user.password);
}
