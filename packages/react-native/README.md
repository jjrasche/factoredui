# @factoredui/react-native

React Native component registry and capture adapter for FactoredUI SDUI.

Part of the [FactoredUI](https://github.com/jjrasche/factoredui) monorepo.

## Install

```bash
npm install @factoredui/core @factoredui/react @factoredui/react-native @supabase/supabase-js
```

## Setup

```tsx
import { createClient } from '@supabase/supabase-js'
import { Provider } from '@factoredui/react'
import { createRnAdapter, createComponentRegistry } from '@factoredui/react-native'

const supabase = createClient(url, anonKey, {
  db: { schema: 'factoredui' }
})

const adapter = createRnAdapter()

function App() {
  return (
    <Provider supabase={supabase} adapter={adapter} platform="ios">
      <YourApp />
    </Provider>
  )
}
```

## Component Registry

Create a themed registry of 20 SDUI primitives for the renderer:

```tsx
import { createComponentRegistry } from '@factoredui/react-native'

const components = createComponentRegistry({
  primary: '#007AFF',
  background: '#FFFFFF',
  text: '#000000',
  border: '#E5E5E5',
  error: '#FF3B30',
  success: '#34C759',
})
```

### Included Primitives

Layout: `Box`, `ScrollView`, `Grid`
Content: `Text`, `Image`, `Icon`, `Divider`, `Spacer`
Input: `Button`, `TextInput`, `Toggle`, `Slider`, `Select`
Feedback: `Chip`, `Card`, `Modal`, `Tabs`
List: `List`

All primitives accept theme tokens and render as standard React Native components.

### Rendering SDUI Specs

```tsx
import { renderSpec } from '@factoredui/react'
import { loadSpec, createSpecStorage, devSignatureVerifier } from '@factoredui/core'
import { createComponentRegistry } from '@factoredui/react-native'

const components = createComponentRegistry(theme)
const spec = await loadSpec(supabase, 'home-hero', storage, devSignatureVerifier)

function HeroSection() {
  return <>{renderSpec(spec.tree, components, actionRegistry, dataSources)}</>
}
```

## Capture Adapter

`createRnAdapter()` implements the `CaptureAdapter` interface for React Native:

- Session ID stored via `AsyncStorage`
- Session metadata from `Platform`, `Dimensions`, `PixelRatio`
- Offline queue persistence via `AsyncStorage`
- App state change handler for unload events

## Path Context

Use the same `Flow`/`Page`/`Component`/`Element` providers from `@factoredui/react` — they work identically on React Native (without DOM data attributes).

```tsx
import { Flow, Page, Component } from '@factoredui/react'

<Flow name="onboarding">
  <Page name="profile">
    <Component name="avatar-picker">
      {/* RN components here */}
    </Component>
  </Page>
</Flow>
```

## Peer Dependencies

- `@factoredui/core ^0.2.0`
- `@factoredui/react ^0.2.0`
- `react ^18.0.0 || ^19.0.0`
- `react-native >=0.73.0`

## License

MIT
