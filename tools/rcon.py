#!/usr/bin/env python3
"""Minecraft RCON 客户端（dev server 调试命令用）。

用法: python tools/rcon.py <命令> [命令...]
默认连接 localhost:25575 / 密码 primalstinct-dev（可用 RCON_PORT / RCON_PASS 覆盖）。
对应 run/server/server.properties 的 enable-rcon / rcon.port / rcon.password。
"""
import os
import socket
import struct
import sys

HOST = "localhost"
PORT = int(os.environ.get("RCON_PORT", "25575"))
PASSWORD = os.environ.get("RCON_PASS", "primalstinct-dev")

SERVERDATA_AUTH = 3
SERVERDATA_AUTH_RESPONSE = 2
SERVERDATA_EXECCOMMAND = 2
SERVERDATA_RESPONSE_VALUE = 0


def send_packet(sock, request_id, ptype, payload):
    data = struct.pack("<ii", request_id, ptype) + payload.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(data)) + data)


def recv_packet(sock):
    raw_len = sock.recv(4)
    if len(raw_len) < 4:
        raise ConnectionError("连接已关闭")
    (length,) = struct.unpack("<i", raw_len)
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise ConnectionError("数据不完整")
        data += chunk
    request_id, ptype = struct.unpack("<ii", data[:8])
    return request_id, ptype, data[8:-2].decode("utf-8", errors="replace")


def main():
    commands = sys.argv[1:]
    if not commands:
        print(__doc__)
        return 1
    with socket.create_connection((HOST, PORT), timeout=10) as sock:
        send_packet(sock, 1, SERVERDATA_AUTH, PASSWORD)
        request_id, _, _ = recv_packet(sock)
        if request_id == -1:
            print("RCON 认证失败（检查 server.properties 的 rcon.password）", file=sys.stderr)
            return 1
        for command in commands:
            send_packet(sock, 2, SERVERDATA_EXECCOMMAND, command)
            _, _, body = recv_packet(sock)
            print(f"$ {command}\n{body}".rstrip())
    return 0


if __name__ == "__main__":
    sys.exit(main())
