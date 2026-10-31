// Position filter of the home page player cards. Goalies only show under All.
export const POSITION_FILTERS = [
  { value: 'all', label: 'All', title: 'All players' },
  { value: 'F', label: 'F', title: 'Forwards' },
  { value: 'D', label: 'D', title: 'Defensemen' }
]

// NHL position codes: C, L and R are forwards, D defensemen, G goalies
const positionGroup = (positionCode) => {
  if (positionCode === 'D' || positionCode === 'G') return positionCode
  return positionCode ? 'F' : null
}

export const matchesPosition = (player, filter) =>
  !filter || filter === 'all' || positionGroup(player.positionCode) === filter
