import { ref, watch } from 'vue'
import { useFavorites } from './useFavorites'

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

// '' follows the viewer's own time zone
export const TIME_ZONES = [
  { value: '', label: 'Your time zone' },
  { value: 'America/New_York', label: 'Eastern (ET)' },
  { value: 'America/Chicago', label: 'Central (CT)' },
  { value: 'America/Denver', label: 'Mountain (MT)' },
  { value: 'America/Phoenix', label: 'Arizona (MST)' },
  { value: 'America/Los_Angeles', label: 'Pacific (PT)' },
  { value: 'America/Anchorage', label: 'Alaska (AKT)' },
  { value: 'Pacific/Honolulu', label: 'Hawaii (HT)' }
]

export const THEMES = [{ value: 'retro', label: 'Retro' }]

// Saved card orders are kept to known keys, with cards added since appended
export const normalizeOrder = (order) => {
  const known = (Array.isArray(order) ? order : []).filter(key => DEFAULT_ORDER.includes(key))
  return [...known, ...DEFAULT_ORDER.filter(key => !known.includes(key))]
}

// Module-level so every page shares one copy
const mobileCardOrder = ref([...DEFAULT_ORDER])
const timeZone = ref('')
const theme = ref('retro')

// Like favorites, settings are only remembered between visits once storage
// consent is given; otherwise they last for the current visit.
const { consentGiven, checkConsent } = useFavorites()

const load = () => {
  if (checkConsent() !== true) return
  try {
    const saved = JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}')
    mobileCardOrder.value = normalizeOrder(saved.mobileCardOrder)
    if (TIME_ZONES.some(zone => zone.value === saved.timeZone)) timeZone.value = saved.timeZone
    if (THEMES.some(option => option.value === saved.theme)) theme.value = saved.theme
  } catch {
    // Keep the defaults
  }
}

const save = () => {
  if (consentGiven.value !== true) return
  try {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify({
      mobileCardOrder: mobileCardOrder.value,
      timeZone: timeZone.value,
      theme: theme.value
    }))
  } catch {
    // Settings still apply for this visit
  }
}

load()
watch([mobileCardOrder, timeZone, theme], save)
// Accepting consent later keeps what was chosen this visit
watch(consentGiven, (given) => { if (given === true) save() })

export function useSettings() {
  const resetMobileCardOrder = () => {
    mobileCardOrder.value = [...DEFAULT_ORDER]
  }

  return { mobileCardOrder, timeZone, theme, resetMobileCardOrder }
}

// Time zone option for Intl/toLocale* calls; undefined means the viewer's own
export const timeZoneOption = () => timeZone.value || undefined
