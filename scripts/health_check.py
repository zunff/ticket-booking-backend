#!/usr/bin/env python3
"""
服务健康检查脚本
检查所有微服务是否正常运行
"""

import requests
import time
import sys
from typing import Dict, List, Tuple

SERVICES = [
    {"name": "ticket-user-service", "url": "http://localhost:8081/actuator/health", "port": 8081},
    {"name": "ticket-ticket-service", "url": "http://localhost:8080/actuator/health", "port": 8080},
    {"name": "ticket-order-service", "url": "http://localhost:8082/actuator/health", "port": 8082},
    {"name": "ticket-stock-service", "url": "http://localhost:8083/actuator/health", "port": 8083},
    {"name": "ticket-gateway-service", "url": "http://localhost:9000/actuator/health", "port": 9000},
]

INFRA_SERVICES = [
    {"name": "MySQL", "host": "localhost", "port": 3306},
    {"name": "Redis", "host": "localhost", "port": 6379},
    {"name": "Nacos", "url": "http://localhost:8848/nacos/v1/console/health/readiness"},
    {"name": "Kafka", "host": "localhost", "port": 9093},
]


def check_http_service(name: str, url: str, timeout: int = 5) -> Tuple[bool, str]:
    try:
        response = requests.get(url, timeout=timeout)
        if response.status_code == 200:
            return True, "OK"
        return False, f"HTTP {response.status_code}"
    except requests.exceptions.ConnectionError:
        return False, "Connection refused"
    except requests.exceptions.Timeout:
        return False, "Timeout"
    except Exception as e:
        return False, str(e)


def check_tcp_service(name: str, host: str, port: int, timeout: int = 3) -> Tuple[bool, str]:
    import socket
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(timeout)
        result = sock.connect_ex((host, port))
        sock.close()
        if result == 0:
            return True, "OK"
        return False, "Connection refused"
    except Exception as e:
        return False, str(e)


def check_microservices() -> Dict[str, Tuple[bool, str]]:
    results = {}
    print("\n" + "=" * 60)
    print("  微服务健康检查")
    print("=" * 60)
    
    for service in SERVICES:
        success, message = check_http_service(service["name"], service["url"])
        results[service["name"]] = (success, message)
        
        status = "✅" if success else "❌"
        print(f"  {status} {service['name']:<25} Port: {service['port']:<5} - {message}")
    
    return results


def check_infrastructure() -> Dict[str, Tuple[bool, str]]:
    results = {}
    print("\n" + "=" * 60)
    print("  基础设施健康检查")
    print("=" * 60)
    
    for service in INFRA_SERVICES:
        if "url" in service:
            success, message = check_http_service(service["name"], service["url"])
        else:
            success, message = check_tcp_service(service["name"], service["host"], service["port"])
        results[service["name"]] = (success, message)
        
        status = "✅" if success else "❌"
        port_str = f"Port: {service.get('port', 'N/A')}" if 'port' in service else ""
        print(f"  {status} {service['name']:<25} {port_str:<12} - {message}")
    
    return results


def check_nacos_services() -> Tuple[bool, str]:
    print("\n" + "=" * 60)
    print("  Nacos 服务注册检查")
    print("=" * 60)
    
    try:
        response = requests.get(
            "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=ticket-booking",
            timeout=5
        )
        if response.status_code == 200:
            data = response.json()
            hosts = data.get("hosts", [])
            if hosts:
                print(f"  ✅ 已注册服务实例: {len(hosts)} 个")
                for host in hosts:
                    print(f"     - {host.get('serviceName', 'unknown')}: {host.get('ip')}:{host.get('port')}")
                return True, f"{len(hosts)} instances"
            return False, "No instances registered"
        return False, f"HTTP {response.status_code}"
    except Exception as e:
        return False, str(e)


def main():
    print("\n" + "=" * 60)
    print("  高并发抢票系统 - 健康检查")
    print("=" * 60)
    
    infra_results = check_infrastructure()
    service_results = check_microservices()
    
    all_success = all(r[0] for r in infra_results.values()) and all(r[0] for r in service_results.values())
    
    print("\n" + "=" * 60)
    if all_success:
        print("  ✅ 所有服务运行正常!")
    else:
        print("  ❌ 部分服务异常，请检查日志!")
    print("=" * 60 + "\n")
    
    return 0 if all_success else 1


if __name__ == "__main__":
    sys.exit(main())
