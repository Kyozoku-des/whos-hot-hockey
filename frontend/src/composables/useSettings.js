import { ref, watch } from 'vue'
import { useFavorites } from './useFavorites'
import { COLOR_SCHEMES, DEFAULT_COLOR_SCHEME, isColorScheme } from '../themes/colorSchemes.js'

const SETTINGS_KEY = 'nhl_settings'

// Home page cards in their default order. HomePage pairs each key with its
// table component; the label tells the two "Last 10 games" cards apart here.
export const HOME_CARDS = [
  { key: 'favorites', title: 'Favorites', label: 'Favorites' },
  { key: 'standings', title: 'Player standings', label: 'Player standings' },
  { key: 'streaks', title: 'Point streaks', label: 'Point streaks' },
  { key: 'hot-players', title: 'Last 10 games', label: 'Last 10 games (players)' },
  { key: 'team-standings', title: 'Team standings', label: 'Team standings' },
  { key: 'win-streaks', title: 'Win streaks', label: 'Win streaks' },
  { key: 'team-hot', title: 'Last 10 games', label: 'Last 10 games (teams)' }
]

const DEFAULT_ORDER = HOME_CARDS.map(card => card.key)

// The zone the browser reports from the device's settings, e.g. "Europe/Stockholm"
const deviceTimeZone = () => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone?.replaceAll('_', ' ') || ''
  } catch {
    return ''
  }
}

// '' follows the viewer's own time zone
const deviceZone = deviceTimeZone()
export const TIME_ZONES = [
  { value: '', label: deviceZone ? `Your time zone (${deviceZone})` : 'Your time zone' },
  { value: 'America/New_York', label: 'Eastern (ET)' },
  { value: 'America/Chicago', label: 'Central (CT)' },
  { value: 'America/Denver', label: 'Mountain (MT)' },
  { value: 'America/Phoenix', label: 'Arizona (MST)' },
  { value: 'America/Los_Angeles', label: 'Pacific (PT)' },
  { value: 'America/Anchorage', label: 'Alaska (AKT)' },
  { value: 'Pacific/Honolulu', label: 'Hawaii (HT)' }
]

// A theme sets the look (font, borders); a color scheme recolors any theme
export const THEMES = [
  { value: 'modern', label: 'Modern' },
  { value: 'retro', label: 'Retro' }
]
export const DEFAULT_THEME = 'modern'

// For canvas text (graphs), which can't read the CSS font variables
export const themeFontFamily = (value) =>
  value === 'retro' ? 'Minecraft, sans-serif' : 'Figtree, system-ui, sans-serif'
export { COLOR_SCHEMES }

// Saved card orders are kept to known keys, with cards added since appended
export const normalizeOrder = (order) => {
  const known = (Array.isArray(order) ? order : []).filter(key => DEFAULT_ORDER.includes(key))
  return [...known, ...DEFAULT_ORDER.filter(key => !known.includes(key))]
}

// Module-level so every page shares one copy
const mobileCardOrder = ref([...DEFAULT_ORDER])
const timeZone = ref('')
const theme = ref(DEFAULT_THEME)
const colorScheme = ref(DEFAULT_COLOR_SCHEME)

// Same storage rules as favorites: localStorage once consent is given (kept
// between visits), sessionStorage if declined (kept until the tab closes), and
// memory only until the storage banner is answered.
const { consentGiven, checkConsent } = useFavorites()

const settingsStorage = () => {
  if (consentGiven.value === true) return localStorage
  if (consentGiven.value === false) return sessionStorage
  return null
}

const load = () => {
  checkConsent()
  try {
    const storage = settingsStorage()
    if (!storage) return
    const saved = JSON.parse(storage.getItem(SETTINGS_KEY) || '{}')
    mobileCardOrder.value = normalizeOrder(saved.mobileCardOrder)
    if (TIME_ZONES.some(zone => zone.value === saved.timeZone)) timeZone.value = saved.timeZone
    // Saved as siteTheme: the old `theme` field always held 'retro', from before
    // themes could be picked, and shouldn't keep those visitors off the new default
    if (THEMES.some(option => option.value === saved.siteTheme)) theme.value = saved.siteTheme
    if (isColorScheme(saved.colorScheme)) colorScheme.value = saved.colorScheme
  } catch {
    // Keep the defaults
  }
}

const save = () => {
  try {
    settingsStorage()?.setItem(SETTINGS_KEY, JSON.stringify({
      mobileCardOrder: mobileCardOrder.value,
      timeZone: timeZone.value,
      siteTheme: theme.value,
      colorScheme: colorScheme.value
    }))
  } catch {
    // Settings still apply for this visit
  }
}

// The stylesheet keys off these attributes (see themes/color-schemes.css). They
// may already be set by public/theme-init.js; this keeps them in step with the
// checked values, e.g. dropping a saved scheme that no longer exists.
const applyToPage = () => {
  const root = document.documentElement
  root.dataset.theme = theme.value
  if (colorScheme.value === DEFAULT_COLOR_SCHEME) delete root.dataset.colorScheme
  else root.dataset.colorScheme = colorScheme.value
}

load()
applyToPage()
watch([mobileCardOrder, timeZone, theme, colorScheme], save)
watch([theme, colorScheme], applyToPage)
// Answering the banner later keeps what was chosen so far, in the storage that
// answer allows, and clears the copy from the other one
watch(consentGiven, (given) => {
  try {
    if (given === true) sessionStorage.removeItem(SETTINGS_KEY)
    if (given === false) localStorage.removeItem(SETTINGS_KEY)
  } catch {
    // Nothing to clear
  }
  save()
})

export function useSettings() {
  const resetMobileCardOrder = () => {
    mobileCardOrder.value = [...DEFAULT_ORDER]
  }

  return { mobileCardOrder, timeZone, theme, colorScheme, resetMobileCardOrder }
}

// Time zone option for Intl/toLocale* calls; undefined means the viewer's own
export const timeZoneOption = () => timeZone.value || undefined
