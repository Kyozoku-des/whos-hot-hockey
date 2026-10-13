// Applies the saved theme and color scheme before the page is first drawn, so
// a returning visitor never sees the default colors flash. Loaded as a classic,
// blocking script from <head>; the app re-checks both values once it starts.
(function () {
  var saved = null
  try {
    saved = localStorage.getItem('nhl_settings') || sessionStorage.getItem('nhl_settings')
  } catch (error) {
    return
  }
  if (!saved) return
  try {
    var settings = JSON.parse(saved) || {}
    var root = document.documentElement
    // siteTheme, not the older theme field (see useSettings.js)
    if (typeof settings.siteTheme === 'string' && /^[a-z]+$/.test(settings.siteTheme)) {
      root.setAttribute('data-theme', settings.siteTheme)
    }
    // Team abbreviations; anything else has no stylesheet rule and keeps the default
    if (typeof settings.colorScheme === 'string' && /^[A-Z]{3}$/.test(settings.colorScheme)) {
      root.setAttribute('data-color-scheme', settings.colorScheme)
    }
  } catch (error) {
    // Keep the defaults
  }
})()
