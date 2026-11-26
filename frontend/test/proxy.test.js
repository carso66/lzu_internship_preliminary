import { describe, expect, it } from 'vitest'
import config, { resolveApiTarget } from '../vite.config.js'

describe('resolveApiTarget 代理目标优先级', () => {
  it('什么都不设时默认指向 Spring Boot 的 8080', () => {
    expect(resolveApiTarget({})).toBe('http://127.0.0.1:8080')
  })

  it('只设 BACKEND_PORT 时用该端口', () => {
    expect(resolveApiTarget({ BACKEND_PORT: '8081' })).toBe('http://127.0.0.1:8081')
  })

  it('MOCK_PORT 优先于 BACKEND_PORT，兼容题目给的 mock', () => {
    expect(resolveApiTarget({ MOCK_PORT: '3001', BACKEND_PORT: '8081' })).toBe(
      'http://127.0.0.1:3001',
    )
  })

  it('API_TARGET 优先级最高，可以是任意完整地址', () => {
    expect(
      resolveApiTarget({ API_TARGET: 'http://10.0.0.5:9000', MOCK_PORT: '3001' }),
    ).toBe('http://10.0.0.5:9000')
  })

  it('空字符串视为没设，不当成有效目标', () => {
    expect(resolveApiTarget({ API_TARGET: '', MOCK_PORT: '', BACKEND_PORT: '' })).toBe(
      'http://127.0.0.1:8080',
    )
  })
})

describe('vite 配置里的代理与端口', () => {
  // defineConfig 是恒等函数，函数式配置会原样返回，这里直接调一次拿到配置对象
  const resolved = config({ mode: 'test' })

  it('把 /api 前缀代理到后端，changeOrigin 必须开（否则后端收到 localhost:5173 的 Host）', () => {
    expect(resolved.server.proxy).toHaveProperty('/api')
    expect(resolved.server.proxy['/api'].target).toBe('http://127.0.0.1:8080')
    expect(resolved.server.proxy['/api'].changeOrigin).toBe(true)
  })

  it('固定 5173 且 strictPort，端口被占时明确报错而不是悄悄换端口', () => {
    expect(resolved.server.port).toBe(5173)
    expect(resolved.server.strictPort).toBe(true)
  })

  it('挂上了 vue 插件', () => {
    expect(resolved.plugins).toHaveLength(1)
    const names = resolved.plugins.map((p) => p.name)
    expect(names).toContain('vite:vue')
  })

  it('vitest 跑 node 环境并只收 test 目录下的用例', () => {
    expect(resolved.test.environment).toBe('node')
    expect(resolved.test.include).toEqual(['test/**/*.test.js'])
  })
})
