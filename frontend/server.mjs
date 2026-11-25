#!/usr/bin/env node
/**
 * 前端静态服务 + /api 反向代理（零依赖，Node 18+）
 *
 * 一条命令启动：  npm start        -> http://127.0.0.1:5173
 * 可选环境变量：   PORT=5173 MOCK_PORT=3001 npm start
 *
 * 设计要点：
 *  - 页面统一用相对路径 /api/...，由本服务转发到 mock，避免跨域与端口写错。
 *  - 若 3001 已有 mock 在跑（评审按 README 另开终端启动的情况），直接复用，不重复拉起。
 *  - 否则自动拉起 mock/server.mjs 并等待就绪，保证「一条命令起服务」。
 *  - 全程不修改 mock/ 目录下任何文件。
 */
import http from 'node:http';
import net from 'node:net';
import { spawn } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { dirname, join, extname, normalize, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 5173);
const MOCK_PORT = Number(process.env.MOCK_PORT || 3001);
const MOCK_HOST = '127.0.0.1';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
};

function portOpen(port) {
  return new Promise((resolve) => {
    const socket = net.connect(port, MOCK_HOST);
    socket.setTimeout(600);
    socket.once('connect', () => { socket.destroy(); resolve(true); });
    socket.once('error', () => resolve(false));
    socket.once('timeout', () => { socket.destroy(); resolve(false); });
  });
}

async function waitForMock(tries = 40) {
  for (let i = 0; i < tries; i += 1) {
    if (await portOpen(MOCK_PORT)) return true;
    await new Promise((r) => setTimeout(r, 150));
  }
  return false;
}

let mockChild = null;

async function ensureMock() {
  if (await portOpen(MOCK_PORT)) {
    console.log(`[frontend] 复用已在 ${MOCK_PORT} 端口运行的 mock 服务`);
    return true;
  }
  console.log(`[frontend] 启动 mock 数据服务（端口 ${MOCK_PORT}）`);
  mockChild = spawn(process.execPath, [join(ROOT, 'mock', 'server.mjs')], {
    cwd: ROOT,
    env: { ...process.env, PORT: String(MOCK_PORT) },
    stdio: 'inherit',
  });
  mockChild.on('exit', (code) => {
    if (code && code !== 0) console.error(`[frontend] mock 进程退出，code=${code}`);
  });
  const ready = await waitForMock();
  if (!ready) console.error('[frontend] 警告：mock 未在预期时间内就绪，/api 请求可能失败');
  return ready;
}

function proxy(req, res) {
  const upstream = http.request(
    {
      hostname: MOCK_HOST,
      port: MOCK_PORT,
      path: req.url,
      method: req.method,
      headers: { ...req.headers, host: `${MOCK_HOST}:${MOCK_PORT}` },
    },
    (up) => {
      res.writeHead(up.statusCode, up.headers);
      up.pipe(res);
    },
  );
  upstream.on('error', (e) => {
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ code: 50000, message: `数据服务不可用：${e.message}`, data: null }));
  });
  req.pipe(upstream);
}

async function serveStatic(req, res, pathname) {
  let rel;
  try {
    rel = pathname === '/' ? 'index.html' : decodeURIComponent(pathname).replace(/^[\\/]+/, '');
  } catch {
    res.writeHead(400);
    return res.end('Bad Request');
  }
  const file = join(ROOT, normalize(rel));
  if (file !== ROOT && !file.startsWith(ROOT + sep)) {
    res.writeHead(403);
    return res.end('Forbidden');
  }
  try {
    const body = await readFile(file);
    res.writeHead(200, { 'Content-Type': MIME[extname(file)] || 'application/octet-stream' });
    return res.end(body);
  } catch {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    return res.end('Not Found');
  }
}

const server = http.createServer((req, res) => {
  const pathname = new URL(req.url, `http://${req.headers.host || 'localhost'}`).pathname;
  if (pathname.startsWith('/api/')) return proxy(req, res);
  return serveStatic(req, res, pathname);
});

await ensureMock();
server.listen(PORT, () => {
  console.log(`[frontend] 好物市集前端已启动: http://127.0.0.1:${PORT}`);
  console.log(`[frontend] /api/* 已代理到 http://${MOCK_HOST}:${MOCK_PORT}`);
});

function shutdown() {
  if (mockChild && !mockChild.killed) mockChild.kill();
  process.exit(0);
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
