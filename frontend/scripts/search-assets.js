import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { TEAM_COLORS } from '../src/themes/teamColors.js'
import { HOME_TITLE, HOME_DESCRIPTION, SITE_URL, seoTags } from '../src/seo.js'
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

export function createRobots() {
  return `User-agent: *\nAllow: /\n\nSitemap: ${SITE_URL}/sitemap.xml\n`
}

export function searchAssetsPlugin() {
  let root
  return {
    name: 'search-assets',
    configResolved(config) {
      root = config.root
    },
    transformIndexHtml() {
      return [
        { tag: 'title', children: HOME_TITLE.replaceAll('&', '&amp;'), injectTo: 'head' },
        ...seoTags({ title: HOME_TITLE, description: HOME_DESCRIPTION }).map(({ attribute, key, content }) => ({
          tag: 'meta', attrs: { [attribute]: key, content }, injectTo: 'head'
        }))
      ]
    },
    generateBundle() {
      this.emitFile({ type: 'asset', fileName: 'sitemap.xml', source: createSitemap() })
      this.emitFile({ type: 'asset', fileName: 'robots.txt', source: createRobots() })
    },
    async writeBundle() {
      // Nginx configuration is never part of the public output (including preview).
      const directory = resolve(root, '.build/nginx')
      await mkdir(directory, { recursive: true })
      await writeFile(resolve(directory, 'spa-routes.conf'), createSpaRoutes())
    }
  }
}
