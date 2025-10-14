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

The frontend expects the backend API to provide the following endpoints:

### Player Endpoints
- `GET /api/players/top-scorers?limit=10` - Top point leaders
- `GET /api/players/point-streaks` - Active point streaks
- `GET /api/players/hottest?threshold=1.5&games=5` - Hottest players by PPG
- `GET /api/players/:id` - Player details
- `GET /api/players/:id/game-log` - Player game logs

### Team Endpoints
- `GET /api/teams/standings` - Team standings
- `GET /api/teams/win-streaks` - Team win streaks
- `GET /api/teams/lose-streaks` - Team lose streaks
- `GET /api/teams/:id` - Team details

## Design

The application follows the reference designs provided:
- Green color scheme (primary: #c8e6c9, cards: #9cb89f)
- Dark header and table headers (#3d3d3d, #4a4a4a)
- Responsive layout with grid-based component arrangement
- Clickable table rows for navigation to detail pages

## Key Features

### Interactive Controls
- Search functionality for player streaks
- Adjustable PPG threshold and games lookback for hottest players
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
