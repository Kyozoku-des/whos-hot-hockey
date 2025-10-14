# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Purpose

This repository is only used for frontend development.

## Application overview

The app that is to be developed is a web application that shows NHL statistics. With the USP of calculating which teams and players are "hot" or "cold" right now.

## Agent instructions

1. Only frontend agent should use this CLAUDE.md file
2. Frontend agent can only write code to this branch
3. Frontend agent can only develop to /frontend sub-folder
4. Frontend agent can only push to frontend branch
5. Frontend agent is allowed to pull changes from master branch, but not push to any other branch than frontend branch
6. Frontend agent needs to pull changes from master before developing anything new to allign that the changes will work with the backend

### Requirements of what app should have:

- A table that shows team standings of the current year.
- A table that shows player point standings of the current year. It should include points, games played, goals, and assists at least
- A table that shows current points streaks of players. This needs to be calculated.
- A table that determines how "hot" a player is right now based on point per games during a certain amount of games.
- A table that shows team win streaks.
- A table that shows team losing streaks.
- When clicking on a player, you should be directed to a new page with statistics about that player.
- When clicking on a team, you should be directed to a new page with statistics about that team.

## Frontend specifications

- Use Vue.js

### Vue.js Specific

#### Component Structure

- Use Single File Components (SFC) with `.vue` extension
- Follow the recommended order: `<template>`, `<script>`, `<style>`
- Use Composition API with `<script setup>` for new components
- Keep components focused and single-responsibility

#### Naming Conventions

- **Components**: PascalCase (e.g., `UserProfile.vue`, `ProductCard.vue`)
- **Component files**: Match the component name
- **Props**: camelCase in JavaScript, kebab-case in templates
- **Events**: kebab-case (e.g., `update-user`, `item-clicked`)
- **Composables**: Prefix with `use` (e.g., `useAuth`, `useFetch`)

#### State Management

- Use Vue 3 Composition API with `reactive` and `ref` for local state
- For global state, check if Pinia or Vuex is configured
- Extract reusable logic into composables in `src/composables/`

#### Styling

- Use scoped styles: `<style scoped>`
- Follow the project's CSS methodology (check for Tailwind, CSS Modules, or plain CSS)
- Maintain consistency with existing styling patterns

### Code Quality

#### Best Practices

- **Reactivity**: Use `ref` for primitives, `reactive` for objects
- **Computed properties**: Use for derived state
- **Watchers**: Only when side effects are needed
- **Props validation**: Always define prop types and validation
- **Type safety**: Use TypeScript if configured, or JSDoc for type hints

#### Performance

- Use `v-show` for frequent toggles, `v-if` for conditional rendering
- Implement lazy loading for routes and heavy components
- Avoid unnecessary reactivity with `shallowRef` or `shallowReactive`
- Use `v-once` for static content

### File Organization

```
src/
├── components/     # Reusable UI components
├── views/         # Route-level components
├── composables/   # Reusable composition functions
├── stores/        # State management (Pinia/Vuex)
├── router/        # Vue Router configuration
├── assets/        # Static assets (images, fonts)
├── utils/         # Utility functions
└── types/         # TypeScript type definitions
```

### Common Tasks

#### Creating a New Component

1. Create file in appropriate directory with PascalCase name
2. Use `<script setup>` for new components
3. Define props with TypeScript or PropTypes
4. Add scoped styles if needed
5. Export component (if not using auto-import)

#### Adding a New Route

1. Create view component in `src/views/`
2. Add route definition to router configuration
3. Use lazy loading: `component: () => import('./views/MyView.vue')`
4. Update navigation if needed

#### Creating a Composable

1. Create file in `src/composables/` with `use` prefix
2. Export a function that returns reactive state and methods
3. Follow the pattern: `export function useFeature() { ... }`

## AI Agent Workflow

### Before Making Changes

**STEP 0 - PULL FROM MASTER (MANDATORY):**

**After pulling from master:**

1. Understand available backend endpoints and data structures
2. **Check for breaking changes**: Review backend commits for API changes
3. **Understand constraints**: Know what backend provides before implementing frontend features

**Then proceed with:**

1. **Understand the context**: Read related files and existing patterns
2. **Check dependencies**: Review `package.json` for available libraries
3. **Follow conventions**: Match the existing code style and structure
4. **Verify setup**: Check for Vue 3 vs Vue 2, TypeScript, build tools

### When Writing Code

1. **Stay consistent**: Match indentation, quote style, and formatting
2. **Use existing utilities**: Don't reinvent existing helpers or composables
3. **Preserve functionality**: Ensure changes don't break existing features
4. **Add comments**: Explain complex logic or business rules
5. **Handle errors**: Add proper error handling and user feedback

### After Making Changes

1. **Test locally**: Verify the application runs without errors
2. **Check console**: Ensure no warnings or errors in browser console
3. **Validate types**: If using TypeScript, ensure no type errors
4. **Review changes**: Confirm all modifications align with requirements

## Common Pitfalls to Avoid

- Don't mutate props directly
- Avoid using `var`, always use `const` or `let`
- Don't access DOM directly without `ref` or `nextTick`
- Avoid memory leaks from uncleared timers or event listeners
- Don't mix Options API and Composition API in the same component
- Avoid deep prop drilling; use provide/inject or state management
- Don't forget to clean up in `onBeforeUnmount` when using composables

## Vue 3 Specific Features

- **Composition API**: Primary way to write components
- **`<script setup>`**: Simplified component authoring
- **Teleport**: Render content outside component hierarchy
- **Suspense**: Handle async components gracefully
- **Multiple v-models**: Support multiple two-way bindings
- **Fragments**: Multiple root elements supported

## Resources

- [Vue.js Official Documentation](https://vuejs.org/)
- [Vue Router Documentation](https://router.vuejs.org/)
- [Pinia Documentation](https://pinia.vuejs.org/)
- [Vue.js Style Guide](https://vuejs.org/style-guide/)
- [Composition API FAQ](https://vuejs.org/guide/extras/composition-api-faq.html)

### Additional, but important

- Have security in mind when you work. For example don't use any unsecure references, URL:s or dependencies
