#!/usr/bin/env python3
"""
API接口测试脚本
测试抢票系统的核心API接口
"""

import requests
import json
import time
import random
import threading
from typing import Dict, Any, Optional
from concurrent.futures import ThreadPoolExecutor, as_completed

BASE_URL = "http://localhost:9000/api"

class Colors:
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    END = '\033[0m'


def print_result(success: bool, message: str):
    status = f"{Colors.GREEN}✅ PASS{Colors.END}" if success else f"{Colors.RED}❌ FAIL{Colors.END}"
    print(f"  {status} - {message}")


def make_request(method: str, url: str, data: Optional[Dict] = None, 
                 headers: Optional[Dict] = None, token: Optional[str] = None) -> tuple:
    try:
        if token:
            headers = headers or {}
            headers["Authorization"] = f"Bearer {token}"
        
        if method.upper() == "GET":
            response = requests.get(url, headers=headers, timeout=10)
        elif method.upper() == "POST":
            response = requests.post(url, json=data, headers=headers, timeout=10)
        elif method.upper() == "PUT":
            response = requests.put(url, json=data, headers=headers, timeout=10)
        elif method.upper() == "DELETE":
            response = requests.delete(url, headers=headers, timeout=10)
        else:
            return False, None, "Invalid method"
        
        return True, response, None
    except requests.exceptions.ConnectionError:
        return False, None, "Connection refused"
    except requests.exceptions.Timeout:
        return False, None, "Request timeout"
    except Exception as e:
        return False, None, str(e)


def test_user_login() -> Optional[str]:
    print(f"\n{Colors.BLUE}=== 用户登录测试 ==={Colors.END}")
    
    success, response, error = make_request("POST", f"{BASE_URL}/users/login", {
        "username": "testuser",
        "password": "123456"
    })
    
    if success and response.status_code == 200:
        data = response.json()
        if data.get("code") == 200:
            token = data.get("data", {}).get("token")
            print_result(True, f"用户登录成功，获取Token")
            return token
        else:
            print_result(False, f"登录失败: {data.get('message', 'Unknown error')}")
    else:
        print_result(False, f"登录请求失败: {error or response.status_code if response else 'Unknown'}")
    
    return None


def test_admin_login() -> Optional[str]:
    print(f"\n{Colors.BLUE}=== 管理员登录测试 ==={Colors.END}")
    
    success, response, error = make_request("POST", f"{BASE_URL}/users/login", {
        "username": "admin",
        "password": "123456"
    })
    
    if success and response.status_code == 200:
        data = response.json()
        if data.get("code") == 200:
            token = data.get("data", {}).get("token")
            print_result(True, f"管理员登录成功")
            return token
        else:
            print_result(False, f"登录失败: {data.get('message', 'Unknown error')}")
    else:
        print_result(False, f"登录请求失败: {error or response.status_code if response else 'Unknown'}")
    
    return None


def test_get_tickets(token: Optional[str] = None) -> list:
    print(f"\n{Colors.BLUE}=== 获取票务列表测试 ==={Colors.END}")
    
    success, response, error = make_request("GET", f"{BASE_URL}/tickets", token=token)
    
    if success and response.status_code == 200:
        data = response.json()
        if data.get("code") == 200:
            tickets = data.get("data", [])
            print_result(True, f"获取票务列表成功，共 {len(tickets)} 个票务")
            return tickets
        else:
            print_result(False, f"获取失败: {data.get('message')}")
    else:
        print_result(False, f"请求失败: {error or response.status_code if response else 'Unknown'}")
    
    return []


def test_get_ticket_detail(ticket_id: int, token: Optional[str] = None):
    print(f"\n{Colors.BLUE}=== 获取票务详情测试 ==={Colors.END}")
    
    success, response, error = make_request("GET", f"{BASE_URL}/tickets/{ticket_id}", token=token)
    
    if success and response.status_code == 200:
        data = response.json()
        if data.get("code") == 200:
            ticket = data.get("data", {})
            print_result(True, f"获取票务详情成功: {ticket.get('name', 'Unknown')}")
            return ticket
        else:
            print_result(False, f"获取失败: {data.get('message')}")
    else:
        print_result(False, f"请求失败: {error or response.status_code if response else 'Unknown'}")
    
    return None


def test_book_ticket(ticket_id: int, user_token: str) -> Optional[str]:
    print(f"\n{Colors.BLUE}=== 抢票测试 ==={Colors.END}")
    
    success, response, error = make_request(
        "POST", 
        f"{BASE_URL}/orders/book",
        {"ticketId": ticket_id, "quantity": 1},
        token=user_token
    )
    
    if success and response.status_code == 200:
        data = response.json()
        if data.get("code") == 200:
            order_no = data.get("data", {})
            print_result(True, f"抢票成功！订单号: {order_no}")
            return order_no
        else:
            print_result(False, f"抢票失败: {data.get('message')}")
    else:
        print_result(False, f"请求失败: {error or response.status_code if response else 'Unknown'}")
    
    return None


def test_get_user_orders(user_token: str):
    print(f"\n{Colors.BLUE}=== 获取用户订单测试 ==={Colors.END}")
    
    success, response, error = make_request("GET", f"{BASE_URL}/orders/my", token=user_token)
    
    if success and response.status_code == 200:
        data = response.json()
        if data.get("code") == 200:
            orders = data.get("data", [])
            print_result(True, f"获取用户订单成功，共 {len(orders)} 个订单")
            return orders
        else:
            print_result(False, f"获取失败: {data.get('message')}")
    else:
        print_result(False, f"请求失败: {error or response.status_code if response else 'Unknown'}")
    
    return []


def test_get_stock(ticket_id: int):
    print(f"\n{Colors.BLUE}=== 获取库存测试 ==={Colors.END}")
    
    success, response, error = make_request("GET", f"{BASE_URL}/stock/{ticket_id}")
    
    if success and response.status_code == 200:
        data = response.json()
        if data.get("code") == 200:
            stock = data.get("data", {})
            print_result(True, f"获取库存成功: {stock}")
            return stock
        else:
            print_result(False, f"获取失败: {data.get('message')}")
    else:
        print_result(False, f"请求失败: {error or response.status_code if response else 'Unknown'}")
    
    return None


def test_admin_create_ticket(admin_token: str) -> Optional[int]:
    print(f"\n{Colors.BLUE}=== 管理员创建票务测试 ==={Colors.END}")
    
    ticket_data = {
        "name": f"测试票务_{int(time.time())}",
        "description": "自动化测试创建的票务",
        "price": 99.00,
        "totalStock": 100,
        "startTime": "2024-01-01T00:00:00",
        "endTime": "2024-12-31T23:59:59"
    }
    
    success, response, error = make_request(
        "POST",
        f"{BASE_URL}/admin/tickets",
        ticket_data,
        token=admin_token
    )
    
    if success and response.status_code == 200:
        data = response.json()
        if data.get("code") == 200:
            ticket = data.get("data", {})
            ticket_id = ticket.get("id")
            print_result(True, f"创建票务成功，ID: {ticket_id}")
            return ticket_id
        else:
            print_result(False, f"创建失败: {data.get('message')}")
    else:
        print_result(False, f"请求失败: {error or response.status_code if response else 'Unknown'}")
    
    return None


def test_concurrent_booking(ticket_id: int, user_token: str, concurrent_users: int = 10):
    print(f"\n{Colors.BLUE}=== 并发抢票测试 ({concurrent_users}用户) ==={Colors.END}")
    
    results = {"success": 0, "fail": 0, "errors": []}
    
    def book_task(user_index: int):
        try:
            success, response, error = make_request(
                "POST",
                f"{BASE_URL}/orders/book",
                {"ticketId": ticket_id, "quantity": 1},
                token=user_token
            )
            
            if success and response.status_code == 200:
                data = response.json()
                if data.get("code") == 200:
                    return True, f"User {user_index}: 抢票成功"
                else:
                    return False, f"User {user_index}: {data.get('message')}"
            return False, f"User {user_index}: {error}"
        except Exception as e:
            return False, f"User {user_index}: {str(e)}"
    
    with ThreadPoolExecutor(max_workers=concurrent_users) as executor:
        futures = [executor.submit(book_task, i) for i in range(concurrent_users)]
        for future in as_completed(futures):
            success, message = future.result()
            if success:
                results["success"] += 1
            else:
                results["fail"] += 1
                results["errors"].append(message)
    
    print(f"  成功: {results['success']}, 失败: {results['fail']}")
    if results["errors"][:3]:
        for err in results["errors"][:3]:
            print(f"    {err}")
    
    return results


def main():
    print("\n" + "=" * 60)
    print("  高并发抢票系统 - API接口测试")
    print("=" * 60)
    
    print(f"\n{Colors.YELLOW}提示: 请确保所有服务已启动{Colors.END}")
    
    user_token = test_user_login()
    admin_token = test_admin_login()
    
    tickets = test_get_tickets(user_token)
    
    if tickets:
        ticket_id = tickets[0].get("id")
        test_get_ticket_detail(ticket_id, user_token)
        test_get_stock(ticket_id)
        
        if user_token:
            order_no = test_book_ticket(ticket_id, user_token)
            if order_no:
                test_get_user_orders(user_token)
            
            test_concurrent_booking(ticket_id, user_token, concurrent_users=5)
    
    if admin_token:
        new_ticket_id = test_admin_create_ticket(admin_token)
    
    print("\n" + "=" * 60)
    print("  测试完成!")
    print("=" * 60 + "\n")


if __name__ == "__main__":
    main()
