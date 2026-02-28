import json
import time
import hmac
import hashlib
import base64
import os
import asyncio
import aiohttp
from datetime import datetime
from typing import List, Dict, Any

SECRET_KEY = "ticket-booking-secret-key-for-jwt-token-generation-2024"

def base64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode('utf-8')

def create_jwt_token(user_id: int, username: str, secret_key: str = SECRET_KEY) -> str:
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "userId": user_id,
        "username": username,
        "sub": str(user_id),
        "iat": int(time.time()),
        "exp": int(time.time()) + 24 * 60 * 60
    }
    
    header_encoded = base64url_encode(json.dumps(header, separators=(',', ':')).encode())
    payload_encoded = base64url_encode(json.dumps(payload, separators=(',', ':')).encode())
    
    message = f"{header_encoded}.{payload_encoded}"
    signature = hmac.new(
        secret_key.encode(),
        message.encode(),
        hashlib.sha256
    ).digest()
    signature_encoded = base64url_encode(signature)
    
    return f"{message}.{signature_encoded}"

def generate_test_tokens(count: int, start_user_id: int = 1) -> List[Dict[str, Any]]:
    tokens = []
    for i in range(start_user_id, start_user_id + count):
        username = f"test_user_{i}"
        token = create_jwt_token(i, username)
        tokens.append({
            "userId": i,
            "username": username,
            "token": token
        })
    return tokens

def save_tokens(tokens: List[Dict[str, Any]], output_dir: str):
    os.makedirs(output_dir, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = os.path.join(output_dir, f"tokens_{timestamp}.json")
    with open(filename, 'w') as f:
        json.dump(tokens, f, indent=2)
    print(f"Generated {len(tokens)} tokens, saved to {filename}")
    return filename

def load_tokens(token_file: str) -> List[Dict[str, Any]]:
    with open(token_file, 'r') as f:
        return json.load(f)

class PerformanceTest:
    def __init__(self, base_url: str, ticket_id: int, token_file: str, 
                 total_requests: int = 1000, concurrency: int = 100):
        self.base_url = base_url
        self.ticket_id = ticket_id
        self.total_requests = total_requests
        self.concurrency = concurrency
        self.tokens = load_tokens(token_file)
        self.results = []
        self.success_count = 0
        self.fail_count = 0
        self.error_details = {}
        
    async def book_ticket(self, session: aiohttp.ClientSession, token_data: Dict) -> Dict:
        headers = {"Authorization": f"Bearer {token_data['token']}"}
        url = f"{self.base_url}/api/tickets/book?ticketId={self.ticket_id}&quantity=1"
        
        start_time = time.time()
        try:
            async with session.post(url, headers=headers) as response:
                data = await response.json()
                elapsed = time.time() - start_time
                
                success = response.status == 200 and data.get('code') == 200
                error_msg = data.get('message', 'Unknown error') if not success else None
                
                if error_msg and error_msg not in self.error_details:
                    self.error_details[error_msg] = 0
                if error_msg:
                    self.error_details[error_msg] += 1
                
                return {
                    'userId': token_data['userId'],
                    'success': success,
                    'elapsed': elapsed,
                    'status': response.status,
                    'orderNo': data.get('data') if success else None,
                    'error': error_msg
                }
        except Exception as e:
            elapsed = time.time() - start_time
            error_msg = str(e)
            if error_msg not in self.error_details:
                self.error_details[error_msg] = 0
            self.error_details[error_msg] += 1
            return {
                'userId': token_data['userId'],
                'success': False,
                'elapsed': elapsed,
                'status': 0,
                'error': error_msg
            }
    
    async def run_batch(self, session: aiohttp.ClientSession, token_batch: List[Dict]) -> List[Dict]:
        tasks = [self.book_ticket(session, token) for token in token_batch]
        return await asyncio.gather(*tasks)
    
    async def run_test(self):
        print(f"\n{'='*60}")
        print(f"High Concurrency Ticket Booking Test (With JWT Auth)")
        print(f"{'='*60}")
        print(f"Base URL: {self.base_url}")
        print(f"Ticket ID: {self.ticket_id}")
        print(f"Total Requests: {self.total_requests}")
        print(f"Concurrency: {self.concurrency}")
        print(f"{'='*60}\n")
        
        if len(self.tokens) < self.total_requests:
            print(f"Warning: Only {len(self.tokens)} tokens available, generating more...")
            new_tokens = generate_test_tokens(self.total_requests - len(self.tokens), len(self.tokens) + 1)
            self.tokens.extend(new_tokens)
        
        connector = aiohttp.TCPConnector(limit=self.concurrency * 2)
        timeout = aiohttp.ClientTimeout(total=30)
        
        start_time = time.time()
        
        async with aiohttp.ClientSession(connector=connector, timeout=timeout) as session:
            batch_size = self.concurrency
            token_idx = 0
            
            for i in range(0, self.total_requests, batch_size):
                current_batch_size = min(batch_size, self.total_requests - i)
                token_batch = self.tokens[token_idx:token_idx + current_batch_size]
                token_idx += current_batch_size
                
                batch_start = time.time()
                results = await self.run_batch(session, token_batch)
                batch_time = time.time() - batch_start
                
                for result in results:
                    self.results.append(result)
                    if result['success']:
                        self.success_count += 1
                    else:
                        self.fail_count += 1
                
                print(f"Batch {i//batch_size + 1}: {current_batch_size} requests in {batch_time:.2f}s")
                print(f"  Success: {sum(1 for r in results if r['success'])}, Failed: {sum(1 for r in results if not r['success'])}")
        
        total_time = time.time() - start_time
        self.print_summary(total_time)
        return self.save_results(total_time)
    
    def print_summary(self, total_time: float):
        print(f"\n{'='*60}")
        print(f"Test Summary")
        print(f"{'='*60}")
        print(f"Total Requests: {self.total_requests}")
        print(f"Successful: {self.success_count}")
        print(f"Failed: {self.fail_count}")
        print(f"Success Rate: {self.success_count/self.total_requests*100:.2f}%")
        print(f"Total Time: {total_time:.2f}s")
        print(f"QPS: {self.total_requests/total_time:.2f}")
        
        if self.results:
            elapsed_times = [r['elapsed'] for r in self.results]
            print(f"Avg Response Time: {sum(elapsed_times)/len(elapsed_times)*1000:.2f}ms")
            print(f"Min Response Time: {min(elapsed_times)*1000:.2f}ms")
            print(f"Max Response Time: {max(elapsed_times)*1000:.2f}ms")
        
        if self.error_details:
            print(f"\nError Breakdown:")
            for error, count in sorted(self.error_details.items(), key=lambda x: -x[1]):
                print(f"  {error}: {count}")
    
    def save_results(self, total_time: float) -> str:
        output_dir = "test_results"
        os.makedirs(output_dir, exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = os.path.join(output_dir, f"test_result_{timestamp}.json")
        
        result_data = {
            "timestamp": timestamp,
            "config": {
                "base_url": self.base_url,
                "ticket_id": self.ticket_id,
                "total_requests": self.total_requests,
                "concurrency": self.concurrency
            },
            "summary": {
                "total_requests": self.total_requests,
                "success_count": self.success_count,
                "fail_count": self.fail_count,
                "success_rate": round(self.success_count/self.total_requests*100, 2),
                "total_time_seconds": round(total_time, 2),
                "qps": round(self.total_requests/total_time, 2)
            },
            "error_details": self.error_details,
            "detailed_results": self.results[:100]
        }
        
        with open(filename, 'w') as f:
            json.dump(result_data, f, indent=2)
        print(f"\nResults saved to {filename}")
        return filename

def main():
    import argparse
    parser = argparse.ArgumentParser(description='Ticket Booking Performance Test')
    parser.add_argument('--generate-tokens', type=int, help='Generate N test tokens')
    parser.add_argument('--test', action='store_true', help='Run performance test')
    parser.add_argument('--url', default='http://localhost:8080', help='Base URL')
    parser.add_argument('--ticket-id', type=int, default=1, help='Ticket ID')
    parser.add_argument('--requests', type=int, default=1000, help='Total requests')
    parser.add_argument('--concurrency', type=int, default=100, help='Concurrency')
    parser.add_argument('--token-file', default='test_data/tokens.json', help='Token file path')
    
    args = parser.parse_args()
    
    if args.generate_tokens:
        tokens = generate_test_tokens(args.generate_tokens)
        save_tokens(tokens, 'test_data')
    elif args.test:
        test = PerformanceTest(
            base_url=args.url,
            ticket_id=args.ticket_id,
            token_file=args.token_file,
            total_requests=args.requests,
            concurrency=args.concurrency
        )
        asyncio.run(test.run_test())
    else:
        tokens = generate_test_tokens(2000)
        token_file = save_tokens(tokens, 'test_data')
        print(f"\nTo run test: python {__file__} --test --token-file {token_file}")

if __name__ == '__main__':
    main()
