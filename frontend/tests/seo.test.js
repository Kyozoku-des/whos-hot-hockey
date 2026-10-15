import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { applySeo, entitySeo, routeSeo, HOME_TITLE, HOME_DESCRIPTION, SITE_URL } from '../src/seo.js'
import { TEAM_COLORS } from '../src/themes/teamColors.js'

test('canonical URLs discard filters and fragments without hiding player pages from rendering', () => {
  assert.equal(routeSeo('/?search=McDavid#scores').canonical, SITE_URL + '/')
  assert.equal(routeSeo('/team/BOS/?season=20252026').canonical, SITE_URL + '/team/BOS')
  assert.ok(!routeSeo('/player/8478402').noindex)
  assert.match(entitySeo('player', '/player/8478402', 'Connor McDavid').title, /Connor McDavid NHL Stats/)
  assert.match(routeSeo('/team/BOS').title, /Boston Bruins/)
  for (const path of ['/settings', '/feedback', '/donations', '/unknown', '/team/XXX']) {
    assert.equal(routeSeo(path).noindex, true, path)
  }
})

test('navigating from utility pages to entity pages and home resets all head metadata', () => {
  const elements = new Map()
  const doc = {
    title: '',
    createElement: () => ({ attributes: {}, setAttribute(key, value) { this.attributes[key] = value } }),
    head: {
      querySelector: selector => elements.get(selector),
      appendChild(element) {
        const a = element.attributes
        const selector = a.rel ? `link[rel="${a.rel}"]` : a.name ? `meta[name="${a.name}"]` : `meta[property="${a.property}"]`
        assert.ok(!elements.has(selector), 'head tags must not be duplicated')
        elements.set(selector, element)
      }
    }
  }
  applySeo(routeSeo('/settings'), doc)
  assert.equal(elements.get('meta[name="robots"]').attributes.content, 'noindex, follow')
  applySeo(entitySeo('player', '/player/8478402', 'Connor McDavid'), doc)
  assert.equal(elements.get('meta[name="robots"]').attributes.content, 'index, follow')
  assert.equal(elements.get('link[rel="canonical"]').attributes.href, SITE_URL + '/player/8478402')
  applySeo(routeSeo('/'), doc)
  assert.equal(doc.title, HOME_TITLE)
  assert.equal(elements.get('meta[property="og:title"]').attributes.content, HOME_TITLE)
  assert.equal(elements.get('meta[name="twitter:description"]').attributes.content, HOME_DESCRIPTION)
  assert.equal(elements.get('link[rel="canonical"]').attributes.href, SITE_URL + '/')
})

test('sitemap covers public routes, excludes utility pages and uses the production domain', () => {
  const xml = readFileSync(new URL('../public/sitemap.xml', import.meta.url), 'utf8')
  const locations = [...xml.matchAll(/<loc>(.*?)<\/loc>/g)].map(match => match[1])
  assert.equal(locations.length, Object.keys(TEAM_COLORS).length + 2)
  assert.equal(new Set(locations).size, locations.length)
  for (const url of locations) {
    assert.equal(new URL(url).origin, SITE_URL)
    assert.ok(!routeSeo(new URL(url).pathname).noindex, url)
  }
  for (const code of Object.keys(TEAM_COLORS)) assert.ok(locations.includes(`${SITE_URL}/team/${code}`))
  const robots = readFileSync(new URL('../public/robots.txt', import.meta.url), 'utf8')
  assert.ok(robots.includes(`Sitemap: ${SITE_URL}/sitemap.xml`))
})

test('entry HTML describes the site before JavaScript and matches the home metadata', () => {
  const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8')
  assert.ok(html.includes(HOME_TITLE.replace('&', '&amp;')))
  assert.ok(html.includes(HOME_DESCRIPTION))
  assert.ok(html.includes('<h2>NHL stats, hot players and point streaks</h2>'))
  assert.ok(!html.includes('/vite.svg'))
})
