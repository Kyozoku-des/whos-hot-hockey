import { ref } from 'vue'

// Matches the 768px breakpoint the stylesheets use, for layout that CSS can't
// reach (e.g. Chart.js options). Module-level so one listener serves every caller.
const query = window.matchMedia('(max-width: 768px)')
const isMobile = ref(query.matches)
query.addEventListener('change', (event) => {
  isMobile.value = event.matches
})

export function useIsMobile() {
  return { isMobile }
}
