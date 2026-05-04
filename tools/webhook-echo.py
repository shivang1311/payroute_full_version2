"""
Local webhook receiver for PayRoute testing.
Run: python tools/webhook-echo.py
Then use http://localhost:7070/ as your webhook endpoint URL.
"""
from http.server import BaseHTTPRequestHandler, HTTPServer
from datetime import datetime


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8", errors="replace")
        print(f"\n=== {datetime.now().isoformat()} POST {self.path} ===")
        for k, v in self.headers.items():
            if k.lower().startswith("x-payroute") or k.lower() == "content-type":
                print(f"  {k}: {v}")
        print(f"  Body: {body}")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"received":true}')

    def log_message(self, *args):
        pass  # silence default access log


if __name__ == "__main__":
    port = 7070
    print(f"Webhook echo listening on http://localhost:{port}/ (Ctrl+C to stop)")
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
