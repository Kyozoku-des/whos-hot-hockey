import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { createRouter, createMemoryHistory } from 'vue-router'
import { applySeo, entitySeo, routeSeo, HOME_TITLE, HOME_DESCRIPTION, SITE_URL, installSeoNavigation, unavailableEntitySeo, seoTags } from '../src/seo.js'
import { createSitemap, createSpaRoutes, createRobots, searchAssetsPlugin } from '../scripts/search-assets.js'
import { PAGE_ROUTES } from '../src/router/routes.js'
import { TEAM_COLORS } from '../src/themes/teamColors.js'

test('canonical URLs normalize path case and trailing slashes without hiding player pages', () => {
  assert.equal(routeSeo('/').canonical, SITE_URL + '/')
  assert.equal(routeSeo('/Team/bos/').canonical, SITE_URL + '/team/BOS')
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
  const xml = createSitemap()
  const locations = [...xml.matchAll(/<loc>(.*?)<\/loc>/g)].map(match => match[1])
  assert.equal(locations.length, Object.keys(TEAM_COLORS).length + 2)
  assert.equal(new Set(locations).size, locations.length)
  for (const url of locations) {
    assert.equal(new URL(url).origin, SITE_URL)
    assert.ok(!routeSeo(new URL(url).pathname).noindex, url)
  }
  for (const code of Object.keys(TEAM_COLORS)) assert.ok(locations.includes(`${SITE_URL}/team/${code}`))
  const robots = createRobots()
  assert.ok(robots.includes(`Sitemap: ${SITE_URL}/sitemap.xml`))
})

test('shared HTML has no homepage body or conflicting raw canonical/og:url', () => {
  const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8')
  assert.ok(html.includes('<div id="app"></div>'))
  assert.ok(!html.includes('rel="canonical"'))
  assert.ok(!html.includes('og:url'))
  assert.ok(!html.includes('/vite.svg'))
  const tags = searchAssetsPlugin().transformIndexHtml()
  assert.equal(tags.find(tag => tag.tag === 'title').children, HOME_TITLE.replace('&', '&amp;'))
  assert.equal(tags.find(tag => tag.attrs?.name === 'description').attrs.content, HOME_DESCRIPTION)
  assert.ok(!tags.some(tag => tag.attrs?.rel === 'canonical' || tag.attrs?.property === 'og:url'))
})

test('failed navigation leaves entity metadata intact', () => {
  let afterEach
  const updates = []
  const router = { afterEach(callback) { afterEach = callback } }
  installSeoNavigation(router, metadata => updates.push(metadata))
  afterEach({ path: '/player/123' }, { path: '/' }, undefined)
  assert.equal(updates.length, 1)
  for (const failure of [{ type: 16 }, { type: 4 }, { type: 8 }]) {
    afterEach({ path: '/player/123' }, { path: '/player/123' }, failure)
  }
  assert.equal(updates.length, 1)
})

test('Vue Router duplicate and aborted navigations preserve the loaded player title', async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: PAGE_ROUTES.map(route => ({ ...route, component: {} }))
  })
  let metadata
  installSeoNavigation(router, value => { metadata = value })
  await router.push('/')
  assert.equal(metadata.title, HOME_TITLE)
  await router.push('/player/8478402')
  metadata = entitySeo('player', '/player/8478402', 'Connor McDavid')
  const loaded = metadata
  assert.ok(await router.push('/player/8478402'))
  assert.equal(metadata, loaded)
  await router.push('/player/8478402?season=20252026')
  assert.equal(router.currentRoute.value.query.season, '20252026')
  assert.equal(metadata, loaded)
  await router.push('/player/8478402?season=20252026#game-logs')
  assert.equal(router.currentRoute.value.hash, '#game-logs')
  assert.equal(metadata, loaded)
  const removeGuard = router.beforeEach(() => false)
  assert.ok(await router.push('/team/BOS'))
  assert.equal(metadata, loaded)
  removeGuard()
  await router.push('/team/bos')
  assert.match(metadata.title, /Boston Bruins/)
  assert.equal(metadata.canonical, SITE_URL + '/team/BOS')
})

test('build emits public crawl resources and keeps server config outside public output', async () => {
  const assets = []
  searchAssetsPlugin().generateBundle.call({ emitFile(asset) { assets.push(asset) } })
  assert.equal(assets.find(asset => asset.fileName === 'sitemap.xml').source, createSitemap())
  assert.equal(assets.find(asset => asset.fileName === 'robots.txt').source, createRobots())
  assert.ok(!assets.some(asset => asset.fileName === 'spa-routes.conf'))
  assert.equal(assets.length, 2)
  const root = await mkdtemp(join(tmpdir(), 'nhl-server-assets-'))
  try {
    const plugin = searchAssetsPlugin()
    plugin.configResolved({ root })
    await plugin.writeBundle()
    assert.equal(readFileSync(join(root, '.build/nginx/spa-routes.conf'), 'utf8'), createSpaRoutes())
    assert.ok(!existsSync(join(root, 'dist/spa-routes.conf')))
    assert.ok(!existsSync(join(root, 'public')))
  } finally {
    await rm(root, { recursive: true, force: true })
  }
})

test('nginx routes track shared Vue routes including case-insensitive arbitrary entity IDs', () => {
  const config = createSpaRoutes()
  const matcher = new RegExp(config.match(/location ~\* "(.*?)"/)[1], 'i')
  for (const route of PAGE_ROUTES) assert.ok(matcher.test(route.path.replace(':id', 'example')), route.path)
  for (const path of ['/TEAM/bos', '/Player/123/', '/player/any-id', '/settings']) assert.ok(matcher.test(path), path)
  for (const path of ['/missing', '/team', '/team/BOS/extra', '/api/players']) assert.ok(!matcher.test(path), path)
  assert.ok(createSpaRoutes([...PAGE_ROUTES, { path: '/future', name: 'Future' }]).includes('/future'))
})

test('failed entity loads are noindex and subsequent successful pages become indexable', () => {
  for (const [kind, path] of [['player', '/player/99999999'], ['team', '/team/BOS']]) {
    const failed = unavailableEntitySeo(kind, path)
    assert.equal(failed.noindex, true)
    assert.match(failed.title, /Unavailable/)
    assert.equal(seoTags(failed).find(tag => tag.key === 'robots').content, 'noindex, follow')
    assert.equal(seoTags(entitySeo(kind, path, 'Example')).find(tag => tag.key === 'robots').content, 'index, follow')
  }
})

test('build-time and runtime metadata share the same tags and site name', () => {
  const metadata = { title: HOME_TITLE, description: HOME_DESCRIPTION }
  const injected = searchAssetsPlugin().transformIndexHtml().filter(tag => tag.tag === 'meta')
  assert.deepEqual(injected, seoTags(metadata).map(({ attribute, key, content }) => ({
    tag: 'meta', attrs: { [attribute]: key, content }, injectTo: 'head'
  })))
  const runtime = seoTags({ ...metadata, canonical: SITE_URL + '/' })
  assert.deepEqual(runtime.filter(tag => tag.key !== 'og:url'), seoTags(metadata))
})
