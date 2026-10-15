import { TEAM_COLORS } from './themes/teamColors.js'

export const SITE_URL = 'https://whoshothockey.com'
export const SITE_NAME = "Who's Hot Hockey"
export const HOME_TITLE = `NHL Stats, Hot Players & Point Streaks | ${SITE_NAME}`
export const HOME_DESCRIPTION = 'Track NHL scoring leaders, active point streaks, hot players, team standings and winning streaks. Explore player game logs and team rosters.'

function normalizeSeoPath(path) {
  const normalized = path.replace(/\/+$/, '').toLowerCase() || '/'
  const team = normalized.match(/^\/team\/([^/]+)$/)
  return team ? `/team/${team[1].toUpperCase()}` : normalized
}

export function installSeoNavigation(router, update = applySeo) {
  return router.afterEach((to, from, failure) => {
    // The initial navigation to / has no matched source route. Later query/hash
    // changes reuse the view and must preserve its loaded entity metadata.
    if (failure || (to.path === from.path && from.matched?.length)) return
    update(routeSeo(to.path))
  })
}

export function routeSeo(path) {
  // Vue Router supplies path without query/hash. Match its case-insensitive routes.
  const cleanPath = normalizeSeoPath(path)
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
  const cleanPath = normalizeSeoPath(path)
  const label = kind === 'player' ? 'NHL Stats & Game Logs' : 'Stats, Roster & Standings'
  const description = kind === 'player'
    ? `Explore ${name} NHL goals, assists, points and game-by-game stats. Compare season scoring progress on Who’s Hot Hockey.`
    : `Explore ${name} wins, losses, points, roster and season scoring progress on Who’s Hot Hockey.`
  return { title: `${name} ${label} | Who's Hot Hockey`, description, canonical: `${SITE_URL}${cleanPath}` }
}

export function unavailableEntitySeo(kind, path) {
  return {
    ...routeSeo(path),
    title: `${kind === 'player' ? 'Player' : 'Team'} Unavailable | ${SITE_NAME}`,
    description: `The requested NHL ${kind} could not be loaded. Explore other player and team stats on ${SITE_NAME}.`,
    noindex: true
  }
}

// Shared by the build-time HTML and runtime updates. URL tags are added only
// when the router knows the current page; the common shell has no page URL.
export function seoTags({ title, description, canonical, noindex = false }) {
  const meta = (attribute, key, content) => ({ attribute, key, content })
  const tags = [
    meta('name', 'description', description),
    meta('name', 'robots', noindex ? 'noindex, follow' : 'index, follow'),
    meta('property', 'og:type', 'website'),
    meta('property', 'og:site_name', SITE_NAME),
    meta('property', 'og:title', title),
    meta('property', 'og:description', description),
    meta('name', 'twitter:card', 'summary'),
    meta('name', 'twitter:title', title),
    meta('name', 'twitter:description', description)
  ]
  if (canonical) tags.push(meta('property', 'og:url', canonical))
  return tags
}

export function applySeo(metadata, doc = document) {
  doc.title = metadata.title
  for (const { attribute, key, content } of seoTags(metadata)) {
    let element = doc.head.querySelector(`meta[${attribute}="${key}"]`)
    if (!element) {
      element = doc.createElement('meta')
      element.setAttribute(attribute, key)
      doc.head.appendChild(element)
    }
    element.setAttribute('content', content)
  }
  if (metadata.canonical) {
    let link = doc.head.querySelector('link[rel="canonical"]')
    if (!link) {
      link = doc.createElement('link')
      link.setAttribute('rel', 'canonical')
      doc.head.appendChild(link)
    }
    link.setAttribute('href', metadata.canonical)
  }
}
