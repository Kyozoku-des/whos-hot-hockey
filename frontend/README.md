# NHL Tracker Frontend

A Vue.js 3 application for tracking NHL statistics with a focus on identifying "hot" players and teams.

## Features

### Home Page
- **Top 10 Points**: Table showing the top 10 point leaders with goals (G), assists (A), points (P), and games played (GP)
- **Active Point Streaks**: Searchable table displaying players on current point streaks
- **Hottest Players**: Configurable table showing players with high points-per-game averages over a specified number of recent games

### Player Detail Page
- Player statistics overview
- Points progression chart (placeholder)
- Game-by-game logs with goals, assists, points, and time on ice

### Team Detail Page
- Team statistics and standings information
- Win/loss records and streaks
- Team roster (placeholder)

## Technology Stack

- **Vue 3** - Progressive JavaScript framework
- **Vue Router** - Official router for Vue.js
- **Vite** - Next generation frontend tooling
- **Composition API** - Modern Vue.js API with `<script setup>`

## Project Structure

```
frontend/
├── src/
│   ├── assets/           # Static assets
│   ├── components/       # Reusable Vue components
│   │   ├── TopPointsTable.vue
│   │   ├── PointStreaksTable.vue
│   │   └── HottestPlayersTable.vue
│   ├── composables/      # Reusable composition functions
│   │   └── useApi.js     # API data fetching logic
│   ├── router/           # Vue Router configuration
│   │   └── index.js
│   ├── views/            # Route-level components
│   │   ├── HomePage.vue
│   │   ├── PlayerPage.vue
│   │   └── TeamPage.vue
│   ├── App.vue           # Root component
│   ├── main.js           # Application entry point
│   └── style.css         # Global styles
├── index.html            # HTML entry point
├── vite.config.js        # Vite configuration
└── package.json          # Dependencies and scripts
```

## Setup Instructions

### Prerequisites
- Node.js 20 or higher
- npm or yarn package manager

### Installation

1. Install dependencies:
```bash
npm install
```

2. Configure the backend API URL:
   - Copy `.env.example` to `.env`
   - Update `VITE_API_BASE_URL` with your backend API URL

```bash
cp .env.example .env
```

### Development

Start the development server:
```bash
npm run dev
```

The application will be available at `http://localhost:3000`

### Production Build

Build for production:
```bash
npm run build
```

Preview production build:
```bash
npm run preview
```

## API Integration

The frontend calls the backend REST API under a relative `/api` base. See
[backend/API_REFERENCE.md](../backend/API_REFERENCE.md) for the endpoints and response shapes.

### Public API rate limiting

The container's nginx limits `/api/` to **10 requests/second per client IP**,
with **50 excess requests** allowed as an immediate burst (`nodelay`). Excess
traffic receives **HTTP 429** before reaching the backend. All API endpoints
share the same bucket, including paths with asset extensions. SPA routes and
static assets are unlimited. Clients behind the same NAT share a bucket.

The 10 MB zone is shared by nginx workers, but not across frontend containers;
this is intended for the single-VPS deployment. Rejections appear at `warn`
level in nginx's error log (`docker compose logs frontend`). Tune `rate` and
`burst` in `nginx.conf` if real traffic warrants it. Buckets reset on restart.

With the local `compose.yaml`, Docker's port publishing can make every request
from the host appear to come from the same bridge gateway address, so all local
clients (browser tabs, scripts, load tests) may share one bucket. Unexpected
429s during local testing usually mean that shared bucket is exhausted.

#### Caddy deployment wiring

The local `compose.yaml` has no Caddy. By default, the image trusts **no TCP
peer** for forwarded addresses (`CADDY_TRUSTED_PROXY=unix:`), so direct requests
are limited by their socket address. The official nginx image renders
`nginx/realip.conf.template` at startup; changing the environment requires
recreating the frontend container.

When adding the production stack from issues #21/#23, give Caddy a fixed IP on
a dedicated proxy network and pass **that exact IP** to the frontend container.
For example, these are fragments for the production Compose file, not an
override for the local file:

```yaml
services:
  caddy:
    # Existing image, ports (80/443), config and volumes go here.
    networks:
      proxy:
        ipv4_address: 172.30.40.2
  frontend:
    # Existing frontend image goes here. No published ports in production.
    environment:
      CADDY_TRUSTED_PROXY: "172.30.40.2"
    networks:
      - proxy
      - default # backend-api is reachable here
networks:
  proxy:
    ipam:
      config:
        - subnet: 172.30.40.0/29
```

Choose a subnet that does not overlap the VPS's other networks. Attach only
Caddy and frontend to `proxy`. Do not trust an entire Docker subnet or
`0.0.0.0/0`. Only Caddy should publish public ports; neither frontend nor
backend-api should have public port mappings that bypass the proxy path.

For Caddy directly facing the internet, its Caddyfile route can be:

```caddyfile
nhl.example.com {
    reverse_proxy frontend:3000
}
```

Caddy supplies `X-Forwarded-For` using the connecting client's IP and ignores
untrusted incoming forwarded values by default. nginx accepts the last XFF
address only when the immediate peer matches `CADDY_TRUSTED_PROXY`; it does not
walk further into the chain. nginx then replaces both `X-Real-IP` and
`X-Forwarded-For` sent to the backend with the verified address. No backend
application changes or Caddy plugins are needed. If a CDN or another proxy is
added ahead of Caddy, review the trust chain before enabling that deployment.

#### Verification

With Python 3 and nginx (including `http_realip_module`) installed:

```bash
python3 -m unittest discover -s frontend/tests -v
```

These integration tests start isolated nginx instances and a mock backend.
They cover 429 responses, refill, independent IPv4/IPv6 clients, spoofed
headers from untrusted peers, upstream header sanitization, API paths with
asset extensions, and unlimited SPA/static requests. Set `NGINX_BINARY` if
nginx is not on `PATH`. They do not start the database or call the NHL API.
CI runs them via `.github/workflows/nginx-tests.yml` when the nginx config or
tests change. They are skipped on Windows.

## Design

The application follows the reference designs provided:
- Green color scheme (primary: #c8e6c9, cards: #9cb89f)
- Dark header and table headers (#3d3d3d, #4a4a4a)
- Responsive layout with grid-based component arrangement
- Clickable table rows for navigation to detail pages

## Key Features

### Interactive Controls
- Search for players and teams
- Click-through navigation from tables to detail pages

### Responsive Design
- Mobile-friendly layout
- Grid-based responsive tables
- Adaptive component sizing

### Error Handling
- Loading states for async data
- Error messages for failed API requests
- Graceful fallbacks for missing images

## Development Guidelines

### Component Structure
- Use Composition API with `<script setup>`
- Single File Components (SFC) format
- Scoped styles for component isolation

### Code Style
- camelCase for JavaScript variables
- PascalCase for component names
- Descriptive naming conventions

### State Management
- Composables for shared logic
- Local component state with `ref` and `reactive`
- No global state management (can add Pinia if needed)

## Future Enhancements

- Team standings table on homepage
- Team win/lose streak tables
- Points progression charts with visualization library
- Real-time data updates
- Player and team image integration
- Advanced filtering and sorting
- Dark mode toggle

## Browser Support

Modern browsers with ES6+ support:
- Chrome/Edge (latest)
- Firefox (latest)
- Safari (latest)

## License

MIT
