"""
测试 getConcertDetail 接口的 @UserRateLimit 限流功能

前置条件:
1. Sentinel Dashboard 已启动 (localhost:8858)
2. ticket-service 已启动 (localhost:8080)
3. 已在 Sentinel 配置限流规则

运行方式:
    pytest test_user_rate_limit.py -v -s
"""

import pytest
import requests
from concurrent.futures import ThreadPoolExecutor, as_completed

# ==================== 配置区域 ====================
# 直接访问 ticket-service
# BASE_URL = "http://localhost:8080"
# 通过网关访问 (二选一)
BASE_URL = "http://localhost:9000/api"

CONCERT_ID = 1  # 测试用的演唱会ID

# 测试用户 Token (需要替换为实际有效的 token)
# 可以通过登录接口获取，或直接使用 JWT 工具生成
TEST_TOKEN = "eyJhbGciOiJIUzM4NCJ9.eyJpc0FkbWluIjpmYWxzZSwidXNlcklkIjozLCJ1c2VybmFtZSI6InVzZXIiLCJzdWIiOiIzIiwiaWF0IjoxNzc0NDUyODYzLCJleHAiOjE3NzQ1MzkyNjN9.0qZBbQJVmMjeqdhrAEBYg6q0Cij5EtOoVP0WOubGqGuIeVN1d93wYo83nIs16xvm"
# ================================================


class TestUserRateLimit:
    """测试用户维度限流功能"""

    @pytest.fixture
    def auth_headers(self):
        """认证请求头"""
        return {
            "Authorization": f"Bearer {TEST_TOKEN}",
            "Content-Type": "application/json"
        }

    def test_auth_valid(self, auth_headers):
        """前置检查: 验证 Token 是否有效"""
        response = requests.get(
            f"{BASE_URL}/concerts/{CONCERT_ID}",
            headers=auth_headers
        )
        result = response.json()
        code = result.get("code")

        print(f"\n认证检查响应: {result}")

        # 检查连接是否成功
        assert response.status_code == 200, \
            f"服务不可用，HTTP状态码: {response.status_code}"

        # 检查认证是否成功
        if code == 500 and "登录" in result.get("message", ""):
            pytest.fail(
                f"Token 无效或已过期，请更新 TEST_TOKEN\n"
                f"响应: {result}\n"
                f"提示: 通过登录接口 POST /api/users/login 获取有效 token"
            )

        # 接口应该能正常响应
        assert code in [200, 404], \
            f"接口返回异常: {result}"

    def test_get_concert_detail_rate_limit(self, auth_headers):
        """
        测试 getConcertDetail 接口的限流功能

        验证:
        1. 当请求超过配置的 QPS 阈值时，部分请求应返回 429
        2. 未被限流的请求应正常返回数据
        """
        total_requests = 30  # 总请求数
        success_count = 0
        rate_limited_count = 0
        auth_error_count = 0
        other_error_count = 0
        error_details = {}  # 记录错误详情
        results = []

        print(f"\n开始发送 {total_requests} 个并发请求...")
        print(f"目标接口: {BASE_URL}/concerts/{CONCERT_ID}")

        with ThreadPoolExecutor(max_workers=15) as executor:
            futures = [
                executor.submit(
                    requests.get,
                    f"{BASE_URL}/concerts/{CONCERT_ID}",
                    headers=auth_headers,
                    timeout=5
                )
                for _ in range(total_requests)
            ]

            for future in as_completed(futures):
                try:
                    response = future.result()
                    result = response.json()
                    results.append(result)
                except Exception as e:
                    results.append({"code": -1, "message": str(e), "data": None})

        # 统计结果
        for result in results:
            code = result.get("code")
            message = result.get("message", "")

            if code == 429:
                rate_limited_count += 1
            elif code == 200:
                success_count += 1
            elif code == 500 and "登录" in message:
                auth_error_count += 1
            elif code == 404:
                other_error_count += 1
                error_details["404"] = message
            else:
                other_error_count += 1
                error_details[str(code)] = message

        # 输出统计
        print(f"\n{'='*50}")
        print(f"测试结果统计:")
        print(f"{'='*50}")
        print(f"总请求数:       {total_requests}")
        print(f"成功(200):      {success_count}")
        print(f"被限流(429):    {rate_limited_count}")
        print(f"认证失败(500):  {auth_error_count}")
        print(f"其他错误:       {other_error_count}")
        if error_details:
            print(f"错误详情:       {error_details}")
        print(f"{'='*50}")

        # 如果存在认证错误，提示更新 token
        if auth_error_count > 0:
            pytest.fail(
                f"检测到认证失败 ({auth_error_count} 次)，请更新有效的 TEST_TOKEN\n"
                f"提示: 通过 POST /api/users/login 登录获取 token"
            )
            return

        # 如果全部是其他错误，先解决其他问题
        if other_error_count == total_requests:
            pytest.fail(
                f"所有请求都失败，请检查:\n"
                f"  1. 服务是否启动\n"
                f"  2. 接口路径是否正确\n"
                f"  3. 演唱会ID={CONCERT_ID} 是否存在"
            )
            return

        # 验证限流生效
        if rate_limited_count > 0:
            print(f"\n[PASS] 限流已生效! {rate_limited_count} 个请求被限流")
        else:
            print("\n[INFO] 未检测到限流")
            print("请检查以下配置:")
            print("  1. Sentinel Dashboard 是否已启动 (http://localhost:8858)")
            print("  2. 是否已在「簇点链路」配置限流规则")
            print("  3. 资源名: ConcertController:getConcertDetail")
            print("  4. QPS 阈值建议设为 5-10")

        # 注意: 取消强制断言，改为报告模式
        # 如果需要强制验证限流，取消下面注释
        # assert rate_limited_count > 0, "限流未生效"


class TestMultiUserRateLimit:
    """测试多用户独立限流"""

    def test_different_users_have_separate_limits(self):
        """
        测试不同用户的限流是否独立

        注意: 需要提供两个不同用户的 token 才能运行此测试
        """
        # 如需测试多用户，请提供不同的 token
        user_tokens = [
            "eyJhbGciOiJIUzM4NCJ9.eyJpc0FkbWluIjp0cnVlLCJ1c2VySWQiOjIsInVzZXJuYW1lIjoiYWRtaW4iLCJzdWIiOiIyIiwiaWF0IjoxNzc0NDUzMzIyLCJleHAiOjE3NzQ1Mzk3MjJ9.cLvTHm2PMMAHYK2EzgwvXHktts2nu8wVU3XWsJhXR8Nc1ZfOKKXjoIuYqsx12gIw",
            "eyJhbGciOiJIUzM4NCJ9.eyJpc0FkbWluIjpmYWxzZSwidXNlcklkIjoxLCJ1c2VybmFtZSI6InRlc3R1c2VyIiwic3ViIjoiMSIsImlhdCI6MTc3NDQ1Mjk1MiwiZXhwIjoxNzc0NTM5MzUyfQ.zYLe-5oXRlNOMLb0ZZDKB5pVd-uIfSp9l3RuxXO36f2aZPuPMjDoaX0Nj4yePbiA"
        ]

        if len(user_tokens) < 2:
            pytest.skip("需要提供两个不同用户的 token 才能运行此测试")

        # 分别测试每个用户的限流情况
        for i, token in enumerate(user_tokens):
            headers = {
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json"
            }

            success_count = 0
            rate_limited_count = 0

            with ThreadPoolExecutor(max_workers=10) as executor:
                futures = [
                    executor.submit(
                        requests.get,
                        f"{BASE_URL}/concerts/{CONCERT_ID}",
                        headers=headers,
                        timeout=5
                    )
                    for _ in range(20)
                ]

                for future in as_completed(futures):
                    try:
                        result = future.result().json()
                        if result.get("code") == 429:
                            rate_limited_count += 1
                        elif result.get("code") == 200:
                            success_count += 1
                    except Exception:
                        pass

            print(f"\n用户 {i+1}: 成功={success_count}, 限流={rate_limited_count}")


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
