import { TEAM_COLORS } from './themes/teamColors.js'

export const SITE_URL = 'https://whoshothockey.com'
export const HOME_TITLE = "NHL Stats, Hot Players & Point Streaks | Who's Hot Hockey"
export const HOME_DESCRIPTION = 'Track NHL scoring leaders, active point streaks, hot players, team standings and winning streaks. Explore player game logs and team rosters.'

export function routeSeo(path) {
  // Queries and fragments are UI state, not separate pages for indexing.
  const cleanPath = path.split(/[?#]/)[0].replace(/\/+$/, '') || '/'
  const canonical = `${SITE_URL}${cleanPath}`
  if (cleanPath === '/') return { title: HOME_TITLE, description: HOME_DESCRIPTION, canonical }
  if (cleanPath === '/tips') return {
    title: "How to Read NHL Stats & Streaks | Who's Hot Hockey",
    description: 'Learn how to explore NHL scoring leaders, point streaks, recent form, team standings and player game logs on Who’s Hot Hockey.', canonical
  }
  const team = cleanPath.match(/^\/team\/([A-Z]{3})$/)
  if (team && TEAM_COLORS[team[1]]) return entitySeo('team', cleanPath, TEAM_COLORS[team[1]].name)
  // Keep player pages indexable while JavaScript fetches their names and stats.
  if (/^\/player\/\d+$/.test(cleanPath)) return {
    title: "NHL Player Stats & Game Logs | Who's Hot Hockey",
    description: 'Explore NHL player goals, assists, points and game-by-game scoring stats on Who’s Hot Hockey.', canonical
  }
  const label = {
    '/settings': 'Settings', '/feedback': 'Feedback', '/donations': 'Support the Site'
  }[cleanPath] || 'Page Not Found'
  return { title: `${label} | Who's Hot Hockey`, description: HOME_DESCRIPTION, canonical, noindex: true }
}

export function entitySeo(kind, path, name) {
  const cleanPath = path.split(/[?#]/)[0].replace(/\/+$/, '')
  const label = kind === 'player' ? 'NHL Stats & Game Logs' : 'Stats, Roster & Standings'
  const description = kind === 'player'
    ? `Explore ${name} NHL goals, assists, points and game-by-game stats. Compare season scoring progress on Who’s Hot Hockey.`
    : `Explore ${name} wins, losses, points, roster and season scoring progress on Who’s Hot Hockey.`
  return { title: `${name} ${label} | Who's Hot Hockey`, description, canonical: `${SITE_URL}${cleanPath}` }
}

export function applySeo({ title, description, canonical, noindex = false }, doc = document) {
  doc.title = title
  const meta = (attribute, key, content) => {
    let element = doc.head.querySelector(`meta[${attribute}="${key}"]`)
    if (!element) {
      element = doc.createElement('meta')
      element.setAttribute(attribute, key)
      doc.head.appendChild(element)
    }
    element.setAttribute('content', content)
  }
  meta('name', 'description', description)
  meta('name', 'robots', noindex ? 'noindex, follow' : 'index, follow')
  meta('property', 'og:type', 'website')
  meta('property', 'og:site_name', "Who's Hot Hockey")
  meta('property', 'og:title', title)
  meta('property', 'og:description', description)
  meta('property', 'og:url', canonical)
  meta('name', 'twitter:card', 'summary')
  meta('name', 'twitter:title', title)
  meta('name', 'twitter:description', description)
  let link = doc.head.querySelector('link[rel="canonical"]')
  if (!link) {
    link = doc.createElement('link')
    link.setAttribute('rel', 'canonical')
    doc.head.appendChild(link)
  }
  link.setAttribute('href', canonical)
}
