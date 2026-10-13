<template>
  <InfoPage title="Donations">
    <section v-if="thanked" class="info-section thanks" role="status">
      <h2>Thank you!</h2>
      <p>Your donation went through. It really helps keep Whos Hot Hockey running.</p>
    </section>

    <section class="info-section">
      <h2>Support the site</h2>
      <p>
        Whos Hot Hockey is free and ad-free. If you enjoy it, a one-time donation helps cover
        hosting and keeps new features coming. No account needed.
      </p>

      <template v-if="currencies.length">
        <div v-if="currencies.length > 1" class="currency-switch" role="group" aria-label="Currency">
          <button
            v-for="code in currencies"
            :key="code"
            type="button"
            class="currency-button"
            :class="{ active: currency === code }"
            :aria-pressed="currency === code"
            @click="currency = code"
          >{{ code }}</button>
        </div>

        <div class="amounts" role="group" aria-label="Amount">
          <button
            v-for="(amount, index) in presets"
            :key="amount"
            type="button"
            class="amount-button"
            :class="{ active: !isCustom && selectedIndex === index }"
            :aria-pressed="!isCustom && selectedIndex === index"
            @click="selectPreset(index)"
          >{{ format(amount) }}</button>
          <button
            type="button"
            class="amount-button"
            :class="{ active: isCustom }"
            :aria-pressed="isCustom"
            @click="selectCustom"
          >Other</button>
        </div>

        <label v-if="isCustom" class="custom-amount">
          <span class="custom-label">Amount ({{ currency }})</span>
          <input
            ref="customInput"
            v-model="customAmount"
            type="number"
            inputmode="decimal"
            :min="limits.min"
            :max="limits.max"
            step="any"
            class="custom-input"
            :class="{ invalid: customError }"
          />
          <span v-if="customError" class="custom-error">{{ customError }}</span>
        </label>

        <a
          class="donate-button"
          :class="{ disabled: !checkoutUrl }"
          :href="checkoutUrl || undefined"
          :aria-disabled="!checkoutUrl"
        >Support Us{{ chosenAmount ? ` with ${format(chosenAmount)}` : '' }}</a>

        <p class="info-note">
          Checkout is hosted by Stripe. Pay with Apple Pay, Google Pay<template v-if="currency === 'SEK'">, Swish</template>
          or card where available. No card details pass through this site, and you can still
          change the amount on the next page.
        </p>
      </template>
      <p v-else class="info-note">Donations are not open yet. Check back soon!</p>
    </section>
  </InfoPage>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import InfoPage from '../components/InfoPage.vue'

// One Stripe "customers choose what to pay" Payment Link per currency, set at
// build time. Stripe hosts checkout, so the site holds no payment code or keys.
// The chosen amount is passed as prefilled_amount, which is always in the
// link's own currency, hence a link per currency.
const LINKS = {
  SEK: import.meta.env.VITE_DONATION_URL_SEK || '',
  EUR: import.meta.env.VITE_DONATION_URL_EUR || '',
  USD: import.meta.env.VITE_DONATION_URL_USD || ''
}

// Presets per currency; the middle one starts selected. min follows Stripe's
// minimum charge per currency.
const CURRENCIES = {
  SEK: { presets: [10, 30, 100], min: 3, max: 10000 },
  EUR: { presets: [1, 3, 10], min: 0.5, max: 1000 },
  USD: { presets: [1, 3, 10], min: 0.5, max: 1000 }
}
const DEFAULT_INDEX = 1

// Euro-area time zones, for guessing the currency without asking for location
const EURO_ZONES = [
  'Europe/Amsterdam', 'Europe/Athens', 'Europe/Berlin', 'Europe/Bratislava', 'Europe/Brussels',
  'Europe/Busingen', 'Europe/Dublin', 'Europe/Helsinki', 'Europe/Lisbon', 'Europe/Ljubljana',
  'Europe/Luxembourg', 'Europe/Madrid', 'Europe/Malta', 'Europe/Mariehamn', 'Europe/Monaco',
  'Europe/Paris', 'Europe/Riga', 'Europe/Rome', 'Europe/San_Marino', 'Europe/Tallinn',
  'Europe/Vaduz', 'Europe/Vatican', 'Europe/Vienna', 'Europe/Vilnius', 'Europe/Zagreb',
  'Europe/Andorra', 'Europe/Podgorica', 'Atlantic/Azores', 'Atlantic/Canary', 'Atlantic/Madeira',
  'Asia/Nicosia', 'Asia/Famagusta', 'Europe/Nicosia'
]

const currencies = Object.keys(LINKS).filter(code => LINKS[code])

// Sweden pays in SEK, the euro area in EUR, everyone else in USD. Falls back
// to whatever currency has a link when the guessed one doesn't.
const guessCurrency = () => {
  let zone = ''
  try {
    zone = Intl.DateTimeFormat().resolvedOptions().timeZone || ''
  } catch {
    // No time zone; use the default
  }
  const guess = zone === 'Europe/Stockholm' ? 'SEK' : EURO_ZONES.includes(zone) ? 'EUR' : 'USD'
  return currencies.includes(guess) ? guess : (currencies.includes('USD') ? 'USD' : currencies[0])
}

const route = useRoute()
// Stripe sends donors back here with ?thanks=1 (set as each link's redirect)
const thanked = computed(() => route.query.thanks === '1')

const currency = ref(guessCurrency())
const selectedIndex = ref(DEFAULT_INDEX)
const isCustom = ref(false)
const customAmount = ref('')
const customInput = ref(null)

const presets = computed(() => CURRENCIES[currency.value]?.presets ?? [])
const limits = computed(() => CURRENCIES[currency.value] ?? { min: 0, max: 0 })

// Amounts don't carry over between currencies, so start again from the middle preset
watch(currency, () => {
  selectedIndex.value = DEFAULT_INDEX
  isCustom.value = false
  customAmount.value = ''
})

const customError = computed(() => {
  if (!isCustom.value || customAmount.value === '') return ''
  const amount = Number(customAmount.value)
  const { min, max } = limits.value
  if (!Number.isFinite(amount) || amount < min) return `The smallest amount is ${format(min)}.`
  if (amount > max) return `The largest amount is ${format(max)}.`
  return ''
})

const chosenAmount = computed(() => {
  if (!isCustom.value) return presets.value[selectedIndex.value] ?? null
  if (customAmount.value === '' || customError.value) return null
  // Card networks charge in whole minor units
  return Math.round(Number(customAmount.value) * 100) / 100
})

// SEK, EUR and USD all count in hundredths (öre / cents)
const checkoutUrl = computed(() => {
  const link = LINKS[currency.value]
  if (!link || !chosenAmount.value) return ''
  const url = new URL(link)
  url.searchParams.set('prefilled_amount', String(Math.round(chosenAmount.value * 100)))
  return url.href
})

const format = (amount) =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: currency.value,
    currencyDisplay: currency.value === 'SEK' ? 'code' : 'symbol',
    minimumFractionDigits: Number.isInteger(amount) ? 0 : 2
  }).format(amount)

const selectPreset = (index) => {
  selectedIndex.value = index
  isCustom.value = false
}

const selectCustom = async () => {
  isCustom.value = true
  await nextTick()
  customInput.value?.focus()
}
</script>

<style scoped>
.thanks {
  border-color: var(--color-success, #7dff7d);
}

.thanks h2 {
  color: var(--color-success, #7dff7d);
}

.currency-switch,
.amounts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 1.25rem;
}

.currency-button,
.amount-button {
  padding: 0.5rem 0.9rem;
  font-family: var(--font-family);
  font-size: 0.9rem;
  letter-spacing: calc(1 * var(--tracking));
  color: var(--color-text-primary);
  background-color: var(--color-bg-primary);
  border: var(--color-border-thick) solid var(--color-line);
  border-radius: 8px;
  transition: color 0.2s ease, border-color 0.2s ease;
}

.currency-button {
  font-size: 0.8rem;
  padding: 0.35rem 0.75rem;
}

.amount-button {
  flex: 1 1 5rem;
}

.currency-button:hover,
.amount-button:hover {
  color: var(--color-text-secondary);
  border-color: var(--color-text-secondary);
}

.currency-button.active,
.amount-button.active {
  color: #000;
  background-color: var(--color-text-secondary);
  border-color: var(--color-text-secondary);
}

.custom-amount {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin-top: 1rem;
}

.custom-label {
  font-size: 0.85rem;
}

.custom-input {
  width: 100%;
  max-width: 12rem;
  padding: 0.6rem 0.75rem;
  font-family: var(--font-family);
  font-size: 0.9rem;
  letter-spacing: calc(1 * var(--tracking));
  color: var(--color-text-primary);
  background-color: var(--color-bg-primary);
  border: var(--color-border-thick) solid var(--color-line);
  border-radius: 8px;
}

.custom-input:focus {
  outline: none;
  border-color: var(--color-text-secondary);
}

.custom-input.invalid {
  border-color: var(--color-negative, hsl(0, 85%, 62%));
}

.custom-error {
  font-size: 0.75rem;
  color: var(--color-negative, hsl(0, 85%, 62%));
}

.donate-button {
  display: inline-block;
  margin: 1.25rem 0 1rem;
  padding: 0.75rem 2rem;
  font-size: 1rem;
  color: #000;
  background-color: var(--color-button);
  border-radius: 8px;
  transition: background-color 0.2s ease, transform 0.2s ease;
}

.donate-button:hover:not(.disabled) {
  background-color: var(--color-button-hover);
  transform: translateY(-2px);
}

.donate-button.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
