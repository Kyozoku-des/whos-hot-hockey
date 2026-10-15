// Public page routes are shared with the generated nginx configuration.
// Keep the catch-all client-only so unknown URLs retain HTTP 404 on direct loads.
export const PAGE_ROUTES = [
  { path: '/', name: 'Home' },
  { path: '/player/:id', name: 'Player' },
  { path: '/team/:id', name: 'Team' },
  { path: '/settings', name: 'Settings' },
  { path: '/feedback', name: 'Feedback' },
  { path: '/donations', name: 'Donations' },
  { path: '/tips', name: 'Tips' }
]
