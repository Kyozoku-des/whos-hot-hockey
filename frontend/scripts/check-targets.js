import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { parse } from 'acorn'

// Check every emitted module, including lazy chunks. An absent/empty build fails.
const files = (await readdir('dist', { recursive: true }))
  .filter(file => file.endsWith('.js'))
if (files.length === 0) throw new Error('No built JavaScript found in dist')

for (const file of files) {
  const path = join('dist', file)
  try {
    parse(await readFile(path, 'utf8'), { ecmaVersion: 2020, sourceType: 'module' })
  } catch (error) {
    throw new Error(path + ': ' + error.message, { cause: error })
  }
}
console.log('ES2020 syntax check passed for ' + files.length + ' built module(s)')
