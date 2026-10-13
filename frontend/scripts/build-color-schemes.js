import { writeFile } from 'node:fs/promises'
import { colorSchemeCss } from '../src/themes/colorSchemes.js'

// Regenerate src/themes/color-schemes.css after changing src/themes/teamColors.js
const target = new URL('../src/themes/color-schemes.css', import.meta.url)
await writeFile(target, colorSchemeCss())
console.log('Wrote ' + target.pathname)
