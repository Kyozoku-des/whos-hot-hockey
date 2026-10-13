// Brand colours of the 32 NHL clubs, keyed by the NHL API team abbreviation.
// Main colour first. Source and caveats: specs/003-team-color-themes/research.md
// ANA, BUF, LAK, OTT and UTA still need checking against club brand material.
// After editing, run: node scripts/build-color-schemes.js
export const TEAM_COLORS = {
  ANA: { name: 'Anaheim Ducks', colors: [['Orange', '#FC4C02'], ['Black', '#000000'], ['Gold', '#85714D'], ['White', '#FFFFFF']] },
  BOS: { name: 'Boston Bruins', colors: [['Gold', '#FFB81C'], ['Black', '#000000'], ['White', '#FFFFFF']] },
  BUF: { name: 'Buffalo Sabres', colors: [['Royal Blue', '#003087'], ['Gold', '#FFB81C'], ['White', '#FFFFFF']] },
  CGY: { name: 'Calgary Flames', colors: [['Red', '#C8102E'], ['Gold', '#F1BE48'], ['Black', '#111111'], ['White', '#FFFFFF']] },
  CAR: { name: 'Carolina Hurricanes', colors: [['Red', '#CE1126'], ['Black', '#000000'], ['Silver', '#A4A9AD']] },
  CHI: { name: 'Chicago Blackhawks', colors: [['Red', '#CF0A2C'], ['Black', '#000000'], ['Gold', '#FFD100'], ['Orange', '#FF671B'], ['Green', '#00833E']] },
  COL: { name: 'Colorado Avalanche', colors: [['Burgundy', '#6F263D'], ['Blue', '#236192'], ['Silver', '#A2AAAD'], ['Black', '#000000']] },
  CBJ: { name: 'Columbus Blue Jackets', colors: [['Union Blue', '#002654'], ['Goal Red', '#CE1126'], ['Capital Silver', '#A4A9AD']] },
  DAL: { name: 'Dallas Stars', colors: [['Victory Green', '#006847'], ['Silver', '#8F8F8C'], ['Black', '#111111']] },
  DET: { name: 'Detroit Red Wings', colors: [['Red', '#CE1126'], ['White', '#FFFFFF']] },
  EDM: { name: 'Edmonton Oilers', colors: [['Royal Blue', '#041E42'], ['Orange', '#FF4C00'], ['White', '#FFFFFF']] },
  FLA: { name: 'Florida Panthers', colors: [['Red', '#C8102E'], ['Navy', '#041E42'], ['Gold', '#B9975B']] },
  LAK: { name: 'Los Angeles Kings', colors: [['Black', '#111111'], ['Silver', '#A2AAAD'], ['White', '#FFFFFF']] },
  MIN: { name: 'Minnesota Wild', colors: [['Forest Green', '#154734'], ['Iron Range Red', '#A6192E'], ['Harvest Gold', '#EAAA00'], ['Minnesota Wheat', '#DDCBA4']] },
  MTL: { name: 'Montreal Canadiens', colors: [['Red', '#AF1E2D'], ['Blue', '#192168'], ['White', '#FFFFFF']] },
  NSH: { name: 'Nashville Predators', colors: [['Gold', '#FFB81C'], ['Navy', '#041E42'], ['White', '#FFFFFF']] },
  NJD: { name: 'New Jersey Devils', colors: [['Red', '#CE1126'], ['Black', '#000000'], ['White', '#FFFFFF']] },
  NYI: { name: 'New York Islanders', colors: [['Royal Blue', '#00539B'], ['Orange', '#F47D30'], ['White', '#FFFFFF']] },
  NYR: { name: 'New York Rangers', colors: [['Blue', '#0038A8'], ['Red', '#CE1126'], ['White', '#FFFFFF']] },
  OTT: { name: 'Ottawa Senators', colors: [['Red', '#C52032'], ['Black', '#000000'], ['Gold', '#C2912C']] },
  PHI: { name: 'Philadelphia Flyers', colors: [['Orange', '#F74902'], ['Black', '#000000'], ['White', '#FFFFFF']] },
  PIT: { name: 'Pittsburgh Penguins', colors: [['Black', '#000000'], ['Gold', '#FCB514'], ['White', '#FFFFFF']] },
  SJS: { name: 'San Jose Sharks', colors: [['Deep Pacific Teal', '#006D75'], ['Burnt Orange', '#EA7200'], ['Black', '#000000']] },
  SEA: { name: 'Seattle Kraken', colors: [['Deep Sea Blue', '#001628'], ['Ice Blue', '#99D9D9'], ['Boundless Blue', '#355464'], ['Shadow Blue', '#68A2B9'], ['Red Alert', '#E9072B']] },
  STL: { name: 'St. Louis Blues', colors: [['Blue', '#002F87'], ['Gold', '#FCB514'], ['Navy', '#041E42']] },
  TBL: { name: 'Tampa Bay Lightning', colors: [['Blue', '#002868'], ['White', '#FFFFFF']] },
  TOR: { name: 'Toronto Maple Leafs', colors: [['Blue', '#00205B'], ['White', '#FFFFFF']] },
  UTA: { name: 'Utah Mammoth', colors: [['Mountain Blue', '#6CACE4'], ['Rock Black', '#000000'], ['Salt White', '#FFFFFF']] },
  VAN: { name: 'Vancouver Canucks', colors: [['Blue', '#00205B'], ['Green', '#00843D'], ['Dark Blue', '#041C2C'], ['Grey', '#99999A']] },
  VGK: { name: 'Vegas Golden Knights', colors: [['Gold', '#B4975A'], ['Steel Grey', '#333F42'], ['Red', '#C8102E'], ['Black', '#000000']] },
  WSH: { name: 'Washington Capitals', colors: [['Red', '#C8102E'], ['Navy', '#041E42'], ['White', '#FFFFFF']] },
  WPG: { name: 'Winnipeg Jets', colors: [['Polar Night Blue', '#041E42'], ['Aviator Blue', '#004C97'], ['Red', '#AC162C'], ['Silver', '#A2AAAD']] }
}
