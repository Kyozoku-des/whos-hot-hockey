import assert from 'node:assert/strict'
import { mkdtemp, mkdir, writeFile, rm, realpath } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { createServer } from 'vite'

test('dev server rejects sensitive files with transform queries and Windows aliases', async () => {
  // Long path: Windows runners use an 8.3 TEMP (RUNNER~1), which Vite resolves to a
  // different real path and then refuses every file as outside its allow list.
  const root = await realpath(await mkdtemp(join(tmpdir(), 'whoshot-vite-security-')))
  const marker = 'SYNTHETIC_SECURITY_TEST_VALUE'
  let server
  try {
    await mkdir(join(root, '.git'))
    const files = ['.env', '.env.production', 'server.pem', 'server.crt', '.git/config']
    for (const file of files) await writeFile(join(root, file), marker)
    await writeFile(join(root, 'allowed.js'), 'export const allowed = true')
    server = await createServer({
      configFile: fileURLToPath(new URL('../vite.config.js', import.meta.url)),
      root,
      logLevel: 'silent',
      optimizeDeps: { noDiscovery: true, include: [] },
      server: { port: 0 }
    })
    await server.listen()
    const url = `http://127.0.0.1:${server.httpServer.address().port}`
    for (const file of files) {
      const aliases = process.platform === 'win32' ? [file, file + '::$DATA'] : [file]
      for (const alias of aliases) {
        for (const query of ['', '?raw', '?url', '?inline']) {
          const path = '/' + alias + query
          const response = await fetch(url + path)
          const body = await response.text()
          assert.notEqual(response.status, 200, path)
          assert.ok(!body.includes(marker), path + ' disclosed the fixture')
        }
      }
    }
    assert.equal(server.config.server.host, '127.0.0.1', 'LAN exposure must require an explicit opt-in')
    const allowed = await fetch(url + '/allowed.js')
    assert.equal(allowed.status, 200)
    assert.match(await allowed.text(), /export const allowed = true/)
  } finally {
    if (server) await server.close()
    await rm(root, { recursive: true, force: true })
  }
})
