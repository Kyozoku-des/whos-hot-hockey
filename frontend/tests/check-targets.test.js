import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const script = fileURLToPath(new URL('../scripts/check-targets.js', import.meta.url))

for (const fixture of [
  { name: 'accepts ES2020 modules', code: 'export const value = data?.value ?? 0', status: 0 },
  { name: 'rejects ES2022 syntax in nested chunks', code: 'class Example { static {} }', status: 1 },
  { name: 'rejects an empty build', status: 1 }
]) {
  test(fixture.name, async () => {
    const root = await mkdtemp(join(tmpdir(), 'whoshot-targets-'))
    try {
      await mkdir(join(root, 'dist/assets/chunks'), { recursive: true })
      if (fixture.code) await writeFile(join(root, 'dist/assets/chunks/lazy.js'), fixture.code)
      const result = spawnSync(process.execPath, [script], { cwd: root, encoding: 'utf8' })
      assert.equal(result.status, fixture.status, result.stderr)
      if (fixture.code && fixture.status !== 0) assert.match(result.stderr, /lazy\.js/)
      if (!fixture.code) assert.match(result.stderr, /No built JavaScript/)
    } finally {
      await rm(root, { recursive: true, force: true })
    }
  })
}
