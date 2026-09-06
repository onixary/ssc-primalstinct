#!/usr/bin/env python3
"""IDEA MCP 直连工具（JSON-RPC over SSE transport）。

在 ZCode 未注册 mcp__jetbrains__* 工具时，通过 http://localhost:<port>/sse
直接调用 IDEA 内置 MCP server。端口可用 IDEA_MCP_PORT 环境变量覆盖（默认 64342）。

用法:
  python tools/idea_mcp.py <tool_name> [json_args] [--timeout 秒]
示例:
  python tools/idea_mcp.py get_run_configurations '{"projectPath": "F:/MC Modding/Projects/ssc-primalstinct"}'
"""
import json
import os
import sys
import threading
import time
import urllib.request

BASE = f"http://localhost:{os.environ.get('IDEA_MCP_PORT', '64342')}"


class McpSession:
    def __init__(self):
        self.events = []
        self.endpoint = None
        self.lock = threading.Lock()
        self.reader = threading.Thread(target=self._read_sse, daemon=True)
        self.reader.start()
        deadline = time.time() + 10
        while self.endpoint is None and time.time() < deadline:
            time.sleep(0.05)
        if self.endpoint is None:
            raise RuntimeError("未收到 SSE endpoint 事件（IDEA MCP 服务未响应）")
        self._post({"jsonrpc": "2.0", "id": 0, "method": "initialize", "params": {
            "protocolVersion": "2024-11-05", "capabilities": {},
            "clientInfo": {"name": "ssc-primalstinct-dev", "version": "1.0"}}})
        self._post({"jsonrpc": "2.0", "method": "notifications/initialized"})
        if self._wait_id(0, 10) is None:
            raise RuntimeError("initialize 响应超时")

    def _read_sse(self):
        try:
            with urllib.request.urlopen(BASE + "/sse", timeout=300) as resp:
                event = {}
                for raw in resp:
                    line = raw.decode("utf-8").rstrip("\r\n")
                    if line == "":
                        if "event" in event or "data" in event:
                            self._dispatch(event)
                        event = {}
                        continue
                    if ":" in line:
                        key, value = line.split(":", 1)
                        key = key.strip()
                        if key in ("data", "event"):
                            event[key] = value.lstrip()
        except Exception as e:  # noqa: BLE001
            print(f"[sse reader terminated] {e}", file=sys.stderr)

    def _dispatch(self, event):
        if event.get("event") == "endpoint" and "data" in event:
            self.endpoint = BASE + event["data"]
            return
        if "data" not in event:
            return
        try:
            msg = json.loads(event["data"])
        except json.JSONDecodeError:
            return
        with self.lock:
            self.events.append(msg)

    def _post(self, payload, timeout=30):
        req = urllib.request.Request(
            self.endpoint, data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"}, method="POST")
        # JetBrains 对 execute_run_configuration 等长耗时工具会保持 POST 连接直到完成，
        # 读超时必须与工具超时对齐（timeout 作用于单次 recv，流式响应会不断重置）
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8")
        if "Accepted" not in body:
            raise RuntimeError(f"消息被拒绝: {body}")

    def _wait_id(self, msg_id, timeout):
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self.lock:
                for i, msg in enumerate(self.events):
                    if msg.get("id") == msg_id:
                        return self.events.pop(i)
            time.sleep(0.1)
        return None

    def call_tool(self, name, arguments, timeout):
        req_id = int(time.time() * 1000) % 100000000
        self._post({"jsonrpc": "2.0", "id": req_id, "method": "tools/call",
                    "params": {"name": name, "arguments": arguments}},
                   timeout=timeout + 30)
        msg = self._wait_id(req_id, timeout)
        if msg is None:
            raise TimeoutError(f"工具 {name} 在 {timeout}s 内未返回")
        if "error" in msg:
            raise RuntimeError(json.dumps(msg["error"], ensure_ascii=False))
        return msg["result"]


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 1
    tool = args[0]
    tool_args = {}
    timeout = 120.0
    rest = args[1:]
    if "--timeout" in rest:
        i = rest.index("--timeout")
        timeout = float(rest[i + 1])
        rest = rest[:i] + rest[i + 2:]
    if rest:
        tool_args = json.loads(rest[0])

    session = McpSession()
    result = session.call_tool(tool, tool_args, timeout)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
