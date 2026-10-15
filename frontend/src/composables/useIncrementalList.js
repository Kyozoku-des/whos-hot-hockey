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

  const visible = computed(() => items.value.slice(0, limit.value))
  const hasMore = computed(() => limit.value < items.value.length)

  // A new search starts again from the top
  watch(items, () => {
    limit.value = PAGE_SIZE
  })

  watch(sentinel, (el, _, onCleanup) => {
    if (!el) return
    const observer = new IntersectionObserver((entries) => {
      if (!entries.some(entry => entry.isIntersecting)) return
      limit.value += PAGE_SIZE
      // Observing again reports the new position, so a tall list (an
      // expanded card) keeps filling until the sentinel is out of reach
      nextTick(() => {
        // Gone once every row is shown
        if (sentinel.value !== el) return
        observer.unobserve(el)
        observer.observe(el)
      })
    }, { root: el.closest('.card-content'), rootMargin: LOOKAHEAD })
    observer.observe(el)
    onCleanup(() => observer.disconnect())
  })

  return { visible, hasMore, sentinel }
}
