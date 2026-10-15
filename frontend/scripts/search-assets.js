import { TEAM_COLORS } from '../src/themes/teamColors.js'
import { HOME_TITLE, HOME_DESCRIPTION, SITE_URL } from '../src/seo.js'
import { PAGE_ROUTES } from '../src/router/routes.js'

export function createSitemap() {
  // Stable public pages only; players are discovered through roster/leaderboard links.
  const paths = ['/', '/tips', ...Object.keys(TEAM_COLORS).sort().map(code => `/team/${code}`)]
  return `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${paths.map(path => `  <url><loc>${SITE_URL}${path}</loc></url>`).join('\n')}
</urlset>
`
}

export function createSpaRoutes(routes = PAGE_ROUTES) {
  const patterns = routes.map(({ path }) => {
    if (path === '/') return '/'
    return path.split('/').map(segment => segment.startsWith(':')
      ? '[^/]+'
      : segment.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('/') + '/?'
  })
  // Quote the regex for nginx because it can contain config punctuation.
  return `# Generated from src/router/routes.js. Do not edit.
location ~* "^(?:${patterns.join('|')})$" {
    try_files /index.html =404;
}
`
}

export function searchAssetsPlugin() {
  return {
    name: 'search-assets',
    transformIndexHtml() {
      // The template is shared by every route. Canonical and og:url are added
      // only after the router knows the page, never hardcoded to the homepage.
      const meta = (attribute, key, content) => ({ tag: 'meta', attrs: { [attribute]: key, content }, injectTo: 'head' })
      return [
        { tag: 'title', children: HOME_TITLE.replaceAll('&', '&amp;'), injectTo: 'head' },
        meta('name', 'description', HOME_DESCRIPTION),
        meta('name', 'robots', 'index, follow'),
        meta('property', 'og:type', 'website'),
        meta('property', 'og:site_name', "Who's Hot Hockey"),
        meta('property', 'og:title', HOME_TITLE),
        meta('property', 'og:description', HOME_DESCRIPTION),
        meta('name', 'twitter:card', 'summary'),
        meta('name', 'twitter:title', HOME_TITLE),
        meta('name', 'twitter:description', HOME_DESCRIPTION)
      ]
    },
    generateBundle() {
      this.emitFile({ type: 'asset', fileName: 'sitemap.xml', source: createSitemap() })
      this.emitFile({ type: 'asset', fileName: 'spa-routes.conf', source: createSpaRoutes() })
    }
  }
}
