import { writeFileSync } from 'node:fs'
import { TEAM_COLORS } from '../src/themes/teamColors.js'
import { SITE_URL } from '../src/seo.js'

// Stable public pages only. Player pages are discovered through actual links
// in leaderboards and team rosters; don't invent IDs or modification dates.
const paths = ['/', '/tips', ...Object.keys(TEAM_COLORS).sort().map(code => `/team/${code}`)]
const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${paths.map(path => `  <url><loc>${SITE_URL}${path}</loc></url>`).join('\n')}
</urlset>
`
writeFileSync(new URL('../public/sitemap.xml', import.meta.url), xml)
