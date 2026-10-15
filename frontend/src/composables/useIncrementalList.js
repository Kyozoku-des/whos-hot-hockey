import { computed, nextTick, ref, watch } from 'vue'

// Rows added at a time. The long tables hold every player in the league, and
// rendering them all at once is what makes older phones struggle.
const PAGE_SIZE = 50
// Start adding rows this far before the end of the list comes into view
const LOOKAHEAD = '0px 0px 600px 0px'

// Renders the first PAGE_SIZE items and adds more as the list is scrolled
// down. Bind `sentinel` to an element after the rows; it is watched within
// the card's scroll area, the nearest `.card-content`, or else the page.
export function useIncrementalList(items) {
  const limit = ref(PAGE_SIZE)
  const sentinel = ref(null)
  // Kept while the sentinel is gone (a short search result), for the reset
  let scrollArea = null
  let observer = null

  const visible = computed(() => items.value.slice(0, limit.value))
  const hasMore = computed(() => limit.value < items.value.length)

  // A new search starts again from the top. Scrolled before the new rows
  // render: left at the bottom, scroll anchoring would hold the sentinel in
  // view and the whole list would fill in again. Sightings queued from the
  // old list are dropped for the same reason.
  watch(items, () => {
    limit.value = PAGE_SIZE
    if (scrollArea) scrollArea.scrollTop = 0
    observer?.takeRecords()
  })

  watch(sentinel, (el, _, onCleanup) => {
    if (!el) return
    scrollArea = el.closest('.card-content')
    const current = new IntersectionObserver((entries) => {
      if (!entries.some(entry => entry.isIntersecting)) return
      limit.value += PAGE_SIZE
      // Observing again reports the new position, so a tall list (an
      // expanded card) keeps filling until the sentinel is out of reach
      nextTick(() => {
        // Gone once every row is shown
        if (sentinel.value !== el) return
        current.unobserve(el)
        current.observe(el)
      })
    }, { root: scrollArea, rootMargin: LOOKAHEAD })
    observer = current
    current.observe(el)
    onCleanup(() => {
      current.disconnect()
      if (observer === current) observer = null
    })
  })

  return { visible, hasMore, sentinel }
}
