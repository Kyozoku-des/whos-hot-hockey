import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import vm from 'node:vm'
import { TEAM_COLORS } from '../src/themes/teamColors.js'
import {
  COLOR_SCHEMES,
  DEFAULT_COLOR_SCHEME,
  TEXT_CONTRAST,
  UI_CONTRAST,
  STATUS_COLORS,
  colorSchemeCss,
  colorSchemeFor,
  contrastRatio,
  isColorScheme
} from '../src/themes/colorSchemes.js'

const teamSchemes = COLOR_SCHEMES.filter(scheme => scheme.value !== DEFAULT_COLOR_SCHEME)

test('offers the theme default first, then all 32 clubs by name', () => {
  assert.equal(COLOR_SCHEMES[0].value, DEFAULT_COLOR_SCHEME)
  assert.equal(COLOR_SCHEMES[0].vars, null)
  assert.equal(teamSchemes.length, 32)
  assert.deepEqual(teamSchemes.map(scheme => scheme.value).sort(), Object.keys(TEAM_COLORS).sort())
  const labels = teamSchemes.map(scheme => scheme.label)
  assert.deepEqual(labels, [...labels].sort((a, b) => a.localeCompare(b)))
})

test('palette colors are six-digit uppercase hex codes', () => {
  for (const [abbrev, team] of Object.entries(TEAM_COLORS)) {
    assert.match(abbrev, /^[A-Z]{3}$/)
    for (const [, hex] of team.colors) assert.match(hex, /^#[0-9A-F]{6}$/, abbrev)
  }
})

for (const scheme of teamSchemes) {
  test(`${scheme.value} is readable and in team colors`, () => {
    const vars = scheme.vars
    const page = vars['--color-bg-primary']
    const backgrounds = [page, vars['--color-bg-card']]
    assert.ok(TEAM_COLORS[scheme.value].colors.some(([, hex]) => hex === page), 'page background is a brand color')
    for (const background of backgrounds) {
      assert.ok(contrastRatio(vars['--color-text-primary'], background) >= TEXT_CONTRAST, 'text on ' + background)
      assert.ok(contrastRatio(vars['--color-text-secondary'], background) >= TEXT_CONTRAST, 'highlight on ' + background)
      assert.ok(contrastRatio(vars['--color-border'], background) >= UI_CONTRAST, 'border on ' + background)
    }
    for (const [name, { ratio }] of Object.entries(STATUS_COLORS)) {
      for (const background of backgrounds) {
        assert.ok(contrastRatio(vars[name], background) >= ratio, `${name} on ${background}`)
      }
    }
    // Buttons that fill with the highlight color use black text
    assert.ok(contrastRatio('#000000', vars['--color-text-secondary']) >= TEXT_CONTRAST, 'black on highlight')
  })
}

test('unknown or stale values fall back to the theme default', () => {
  assert.equal(isColorScheme('EDM'), true)
  assert.equal(isColorScheme('ARI'), false)
  assert.equal(isColorScheme(undefined), false)
  assert.equal(colorSchemeFor('ARI').value, DEFAULT_COLOR_SCHEME)
})

test('color-schemes.css matches the palettes', async () => {
  // Windows checkouts may turn LF into CRLF
  const css = (await readFile(new URL('../src/themes/color-schemes.css', import.meta.url), 'utf8')).replace(/\r\n/g, '\n')
  assert.equal(css, colorSchemeCss(), 'run: node scripts/build-color-schemes.js')
  assert.doesNotMatch(css, new RegExp(`data-color-scheme="${DEFAULT_COLOR_SCHEME}"`))
})

const runThemeInit = async (stored) => {
  const source = await readFile(new URL('../public/theme-init.js', import.meta.url), 'utf8')
  const attributes = {}
  const storage = { getItem: key => (key === 'nhl_settings' ? stored : null) }
  vm.runInNewContext(source, {
    localStorage: storage,
    sessionStorage: { getItem: () => null },
    document: { documentElement: { setAttribute: (name, value) => { attributes[name] = value } } }
  })
  return attributes
}

test('theme-init applies saved settings before the app loads', async () => {
  assert.deepEqual(
    await runThemeInit(JSON.stringify({ theme: 'retro', colorScheme: 'EDM' })),
    { 'data-theme': 'retro', 'data-color-scheme': 'EDM' }
  )
})

test('theme-init ignores missing, broken and unexpected values', async () => {
  assert.deepEqual(await runThemeInit(null), {})
  assert.deepEqual(await runThemeInit('{not json'), {})
  assert.deepEqual(
    await runThemeInit(JSON.stringify({ theme: '"><x', colorScheme: 'default' })),
    {}
  )
})
