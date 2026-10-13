import { TEAM_COLORS } from './teamColors.js'

// A color scheme recolors whatever theme is active; the theme keeps its font
// and shapes. 'default' leaves the theme's own colors (Retro's in style.css).
export const DEFAULT_COLOR_SCHEME = 'default'

// WCAG 2.2 AA: normal text 4.5:1, borders and other UI parts 3:1
export const TEXT_CONTRAST = 4.5
export const UI_CONTRAST = 3

const WHITE = '#FFFFFF'
const BLACK = '#000000'

const channels = hex => [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16))

const toHex = rgb => '#' + rgb.map(value => Math.round(value).toString(16).padStart(2, '0')).join('').toUpperCase()

// WCAG relative luminance
export const luminance = (hex) => {
  const [r, g, b] = channels(hex).map((value) => {
    const c = value / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

export const contrastRatio = (a, b) => {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (light + 0.05) / (dark + 0.05)
}

// Blend `amount` (0-1) of `other` into `hex`
export const mix = (hex, other, amount) => {
  const [from, to] = [channels(hex), channels(other)]
  return toHex(from.map((value, i) => value + (to[i] - value) * amount))
}

const readableOn = (color, backgrounds, ratio = TEXT_CONTRAST) =>
  backgrounds.every(background => contrastRatio(color, background) >= ratio)

// How far a color must be lightened to read on the backgrounds, or null when
// even white-ish tints don't
const tintNeeded = (color, backgrounds, ratio = TEXT_CONTRAST) => {
  for (let step = 0; step <= 20; step++) {
    if (readableOn(mix(color, WHITE, step * 0.05), backgrounds, ratio)) return step * 0.05
  }
  return null
}

// Colors that carry a meaning rather than the brand: Retro's value (also the
// CSS fallback where it is used) and the contrast it needs. Each scheme
// lightens them just enough to stand out on its page and cards, e.g. the
// negative red turns pink on a red team's cards.
export const STATUS_COLORS = {
  '--color-positive': { retro: '#4ADE80', ratio: TEXT_CONTRAST },
  '--color-negative': { retro: '#F87171', ratio: TEXT_CONTRAST },
  '--color-success': { retro: '#7DFF7D', ratio: TEXT_CONTRAST },
  // Graph lines and markers are graphics, so 3:1
  '--color-graph-previous': { retro: '#6B7280', ratio: UI_CONTRAST },
  '--color-graph-projection': { retro: '#F04C4C', ratio: UI_CONTRAST }
}

// Brand blacks and navies are too dark to lighten into a recognisable accent
const MIN_ACCENT_LUMINANCE = 0.03
// Up to this much lightening still reads as the brand color
const BRAND_TINT_LIMIT = 0.6
// Below this a page is near black, so cards are set off by lightening instead
const DARK_PAGE_LUMINANCE = 0.01

// Builds the page colors from a team palette (main color first). Text stays
// white, as in Retro, so the page background is the first brand color white
// text reads on. Highlights and borders take the first other brand color that
// reads on both page and card, lightened a little if needed; teams with no
// such color get a light tint of their background.
export const buildColorScheme = (palette) => {
  const hexes = palette.map(([, hex]) => hex)
  const page = hexes.find(hex => contrastRatio(hex, WHITE) >= TEXT_CONTRAST) ??
    mix(hexes[0], BLACK, tintNeeded(hexes[0], [WHITE]) ?? 0.5)
  // Cards sit a shade darker than the page, as in Retro, or lighter on a black page
  const card = luminance(page) < DARK_PAGE_LUMINANCE ? mix(page, WHITE, 0.08) : mix(page, BLACK, 0.35)
  const backgrounds = [page, card]

  const candidates = hexes
    .filter(hex => hex !== page && hex !== WHITE && luminance(hex) >= MIN_ACCENT_LUMINANCE)
    .map(hex => ({ hex, tint: tintNeeded(hex, backgrounds) }))
    .filter(candidate => candidate.tint !== null && candidate.tint <= BRAND_TINT_LIMIT)
  const brand = candidates[0]
  const accent = brand
    ? mix(brand.hex, WHITE, brand.tint)
    : mix(page, WHITE, tintNeeded(page, backgrounds))

  return {
    '--color-bg-primary': page,
    '--color-bg-card': card,
    '--color-text-primary': WHITE,
    '--color-text-secondary': accent,
    '--color-border': accent,
    ...Object.fromEntries(Object.entries(STATUS_COLORS).map(([name, { retro, ratio }]) =>
      [name, mix(retro, WHITE, tintNeeded(retro, backgrounds, ratio))]
    ))
  }
}

const teamSchemes = Object.entries(TEAM_COLORS)
  .map(([abbrev, team]) => ({
    value: abbrev,
    label: team.name,
    swatches: team.colors.map(([, hex]) => hex),
    vars: buildColorScheme(team.colors)
  }))
  .sort((a, b) => a.label.localeCompare(b.label))

export const COLOR_SCHEMES = [
  { value: DEFAULT_COLOR_SCHEME, label: 'Theme default', swatches: [], vars: null },
  ...teamSchemes
]

export const isColorScheme = value => COLOR_SCHEMES.some(scheme => scheme.value === value)

export const colorSchemeFor = value =>
  COLOR_SCHEMES.find(scheme => scheme.value === value) ?? COLOR_SCHEMES[0]

// Contents of color-schemes.css, written by scripts/build-color-schemes.js.
// The stylesheet lets theme-init.js color the page before the app loads.
export const colorSchemeCss = () => [
  '/* Generated by scripts/build-color-schemes.js from src/themes/teamColors.js. Do not edit. */',
  ...teamSchemes.map(scheme => [
    `:root[data-color-scheme="${scheme.value}"] {`,
    ...Object.entries(scheme.vars).map(([name, value]) => `  ${name}: ${value};`),
    '}'
  ].join('\n'))
].join('\n\n') + '\n'
