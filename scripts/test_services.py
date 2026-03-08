#!/usr/bin/env python3
import requests
import json
import sys
from typing import Dict, Any

BASE_URL = "http://localhost:9000"
TIMEOUT = 5

def print_result(test_name: str, success: bool, message: str = ""):
    status = "✅" if success else "❌"
    print(f"{status} {test_name}")
    if message:
        print(f"   {message}")

def test_health_check() -> bool:
    try:
        response = requests.get(f"{BASE_URL}/actuator/health", timeout=TIMEOUT)
        if response.status_code == 200:
            data = response.json()
            if data.get("status") == "UP":
                print_result("Gateway Health Check", True, f"Status: {data['status']}")
                return True
        print_result("Gateway Health Check", False, f"Status code: {response.status_code}")
        return False
    except Exception as e:
        print_result("Gateway Health Check", False, str(e))
        return False

def test_user_login() -> Dict[str, Any]:
    try:
        response = requests.post(
            f"{BASE_URL}/api/users/login",
            json={"username": "testuser", "password": "123456"},
            timeout=TIMEOUT
        )
        if response.status_code == 200:
            data = response.json()
            if data.get("success"):
                token = data.get("data", {}).get("token")
                print_result("User Login", True, f"Token: {token[:20] if token else 'N/A'}...")
                return {"success": True, "token": token}
        print_result("User Login", False, f"Status code: {response.status_code}, Response: {response.text}")
        return {"success": False}
    except Exception as e:
        print_result("User Login", False, str(e))
        return {"success": False}

def test_get_concerts(token: str) -> Dict[str, Any]:
    try:
        headers = {"Authorization": f"Bearer {token}"}
        response = requests.get(
            f"{BASE_URL}/api/concerts",
            headers=headers,
            timeout=TIMEOUT
        )
        if response.status_code == 200:
            data = response.json()
            if data.get("success"):
                concerts = data.get("data", {}).get("records", [])
                print_result("Get Concerts", True, f"Found {len(concerts)} concerts")
                return {"success": True, "concerts": concerts}
        print_result("Get Concerts", False, f"Status code: {response.status_code}, Response: {response.text}")
        return {"success": False}
    except Exception as e:
        print_result("Get Concerts", False, str(e))
        return {"success": False}

def test_get_concert_detail(token: str, concert_id: int = 1) -> Dict[str, Any]:
    try:
        headers = {"Authorization": f"Bearer {token}"}
        response = requests.get(
            f"{BASE_URL}/api/concerts/{concert_id}",
            headers=headers,
            timeout=TIMEOUT
        )
        if response.status_code == 200:
            data = response.json()
            if data.get("success"):
                concert = data.get("data", {})
                grades = concert.get("grades", [])
                print_result("Get Concert Detail", True, f"Concert: {concert.get('name')}, Grades: {len(grades)}")
                return {"success": True, "concert": concert}
        print_result("Get Concert Detail", False, f"Status code: {response.status_code}, Response: {response.text}")
        return {"success": False}
    except Exception as e:
        print_result("Get Concert Detail", False, str(e))
        return {"success": False}

def test_book_ticket(token: str, concert_id: int = 1, grade_id: int = 1, quantity: int = 1) -> Dict[str, Any]:
    try:
        headers = {"Authorization": f"Bearer {token}"}
        response = requests.post(
            f"{BASE_URL}/api/orders/book",
            json={"concertId": concert_id, "gradeId": grade_id, "quantity": quantity},
            headers=headers,
            timeout=TIMEOUT
        )
        data = response.json()
        if response.status_code == 200 and data.get("success"):
            order_data = data.get("data")
            if isinstance(order_data, str):
                print_result("Book Ticket", True, f"Order No: {order_data}")
                return {"success": True, "order_no": order_data}
            elif isinstance(order_data, dict):
                order_no = order_data.get("orderNo")
                print_result("Book Ticket", True, f"Order No: {order_no}")
                return {"success": True, "order_no": order_no}
        else:
            print_result("Book Ticket", False, f"Message: {data.get('message', 'Unknown error')}")
            return {"success": False}
    except Exception as e:
        print_result("Book Ticket", False, str(e))
        return {"success": False}

def test_get_stock(token: str, concert_id: int = 1, grade_id: int = 1) -> bool:
    try:
        headers = {"Authorization": f"Bearer {token}"}
        response = requests.get(
            f"{BASE_URL}/api/stock/{concert_id}/{grade_id}",
            headers=headers,
            timeout=TIMEOUT
        )
        if response.status_code == 200:
            data = response.json()
            if data.get("success"):
                stock = data.get("data")
                if isinstance(stock, int):
                    print_result("Get Stock", True, f"Stock: {stock}")
                else:
                    print_result("Get Stock", True, f"Stock: {stock}")
                return True
        print_result("Get Stock", False, f"Status code: {response.status_code}")
        return False
    except Exception as e:
        print_result("Get Stock", False, str(e))
        return False

def main():
    print("=" * 60)
    print("  高并发抢票系统 - 服务健康检查")
    print("=" * 60)
    print()
    
    all_success = True
    
    print("[1/6] 测试网关健康检查...")
    if not test_health_check():
        all_success = False
        print("\n❌ 网关未启动，停止后续测试")
        sys.exit(1)
    
    print("\n[2/6] 测试用户登录...")
    login_result = test_user_login()
    if not login_result["success"]:
        all_success = False
        print("\n❌ 用户登录失败，停止后续测试")
        sys.exit(1)
    
    token = login_result["token"]
    
    print("\n[3/6] 测试获取演唱会列表...")
    concerts_result = test_get_concerts(token)
    if not concerts_result["success"]:
        all_success = False
    
    print("\n[4/6] 测试获取演唱会详情...")
    concert_detail_result = test_get_concert_detail(token, concert_id=1)
    if not concert_detail_result["success"]:
        all_success = False
    else:
        concert = concert_detail_result["concert"]
        grades = concert.get("grades", [])
        if grades:
            grade_id = grades[0]["id"]
            print(f"\n[5/6] 测试抢票功能 (使用 gradeId: {grade_id})...")
            book_result = test_book_ticket(token, concert_id=1, grade_id=grade_id, quantity=1)
            if not book_result["success"]:
                all_success = False
        else:
            print("\n[5/6] 测试抢票功能...")
            print_result("Book Ticket", False, "No grades available")
            all_success = False
    
    print("\n[6/6] 测试库存查询...")
    if not test_get_stock(token, concert_id=1, grade_id=1):
        all_success = False
    
    print("\n" + "=" * 60)
    if all_success:
        print("✅ 所有测试通过！")
    else:
        print("⚠️  部分测试失败，请检查日志")
    print("=" * 60)
    
    sys.exit(0 if all_success else 1)

if __name__ == "__main__":
    main()
