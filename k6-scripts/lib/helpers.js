/**
 * 通用工具函数
 */

/**
 * 随机整数
 * @param {number} min - 最小值
 * @param {number} max - 最大值
 * @returns {number} 随机整数
 */
export function randomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * 随机选择数组元素
 * @param {Array} array - 数组
 * @returns {*} 随机元素
 */
export function randomChoice(array) {
    return array[randomInt(0, array.length - 1)];
}
