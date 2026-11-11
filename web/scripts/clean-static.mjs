import { rmSync, existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * 构建前清空后端静态资源目录。
 * 因为 outDir 在 Vite 项目根目录之外，Vite 不会自动清空，旧 hash 文件会一直累积。
 */
const here = dirname(fileURLToPath(import.meta.url))
const target = resolve(here, '../../backend/src/main/resources/static')
if (existsSync(target)) {
  rmSync(target, { recursive: true, force: true })
  console.log('已清空静态资源目录：' + target)
}
