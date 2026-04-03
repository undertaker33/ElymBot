# Plugin Redesign Design

Date: 2026-04-03
Scope: `Plugin` information architecture and visual design direction only
Implementation status: Not started

## Context

The current `Plugin` experience combines four different jobs into one long workspace:

- install plugin packages
- review installed plugins
- inspect repository sources
- browse discoverable catalog entries

It also switches into plugin detail inside the same workspace instead of treating detail as a separate page. That causes two problems:

- the homepage does not establish a clear first-screen priority
- high-frequency management tasks compete with lower-frequency infrastructure data

The redesign should improve discovery and installation while keeping installed-plugin management as the primary operational focus.

## Product Direction

The approved direction is:

- full lifecycle support, with installed-plugin management prioritized
- marketplace-like experience in structure, not in color palette
- homepage as overview and decision surface
- plugin detail as a dedicated second-level page
- color styling aligned with the rest of the app

This direction can be summarized as:

`Library-first with market shell`

## Goals

- Make the first screen answer: what needs attention right now?
- Keep plugin discovery prominent without burying management tasks
- Separate homepage responsibilities from single-plugin detail responsibilities
- Make update, compatibility, permission, and runtime risk states visible before entering detail
- Keep the page visually aligned with other AstrBot screens

## Non-Goals

- No implementation changes in this design document
- No new brand palette for the plugin area
- No redesign of unrelated navigation outside the plugin flow
- No attempt to turn the plugin area into a standalone store product

## Homepage Information Architecture

The redesigned homepage is a five-section page. The reading order should move from immediate action to broader discovery.

### 1. Hero + Quick Install

The top of the page should be a market-style hero with a strong title and a concise supporting line. Its role is to frame the page as the plugin hub without changing the app's overall visual language.

The hero includes three primary entry actions:

- `Import local zip`
- `Add repository`
- `Install from link`

These actions should be presented as direct entry points, not as always-expanded stacked forms. Selecting one can open a lightweight inline expander, sheet, or dialog. The goal is to prevent the first screen from being dominated by text fields.

### 2. Health Overview

Immediately below the hero is a compact summary area with four metric cards:

- `Installed`
- `Updates available`
- `Needs review`
- `Sources`

`Needs review` is the key metric. It aggregates the items the user is most likely to act on:

- compatibility problems
- permission escalation review
- runtime failure or suspension states

This section exists to tell the user whether the plugin library is healthy before they read the rest of the page.

### 3. Installed Library

This is the main module of the homepage and should be the visual center of gravity.

It replaces the current installed section with a searchable, filterable plugin library. The minimum top-level filters are:

- `All`
- `Enabled`
- `Updates`
- `Issues`
- `Permission changes`

The installed library should be optimized for scan speed and decision-making, not for exhaustive metadata display.

### 4. Discover

The discover section remains on the homepage to preserve a marketplace feeling, but it becomes a secondary module rather than a co-equal primary section.

Its role is to surface:

- recommended plugins
- newly updated entries
- discoverable install candidates

This section should feel lighter and more browseable than the installed library.

### 5. Repositories

Repository sources move to the bottom of the page as a supporting infrastructure layer.

Each repository card should emphasize:

- source name
- host or origin
- number of entries
- last sync time
- stale or error state

Repositories support discovery and updates, but they are not the main decision surface of the homepage.

## Homepage Card Rules

### Installed Plugin Card

Each installed plugin card should follow a stable four-layer structure:

1. `Identity`
   - plugin name
   - version
   - source
2. `Status`
   - enabled, disabled, update available, incompatible, suspended, and similar states
3. `One-line insight`
   - the single most important next-step message
   - example: `New permissions required before update`
4. `Primary action`
   - exactly one dominant action such as `Open`, `Review`, or `Update`

This keeps the homepage readable at speed and prevents every card from turning into a miniature detail page.

### Discover Card

Discover cards should intentionally differ from installed cards.

They should emphasize:

- recommendation or novelty
- concise summary
- source or category label
- lightweight actions such as `Install` or `View`

Installed cards are management-oriented. Discover cards are browse-oriented.

## Detail Page Architecture

Plugin detail moves to a dedicated second-level page.

The homepage is responsible for surfacing priorities and giving the user a fast way to enter the right plugin. The detail page is responsible for understanding and operating a single plugin.

The detail page uses a six-section structure.

### 1. Top Summary

The first block includes:

- plugin name
- version
- author
- source
- status chips

Only high-value status chips should be shown here, such as:

- `Enabled`
- `Update available`
- `Incompatible`
- `Permission review`
- `Suspended after failures`

This block should let the user determine plugin health in a few seconds.

### 2. Primary Actions

The main action zone appears directly below the summary and includes:

- `Enable` or `Disable`
- `Update`
- `Uninstall`

If a blocking or risky condition exists, a short warning appears above the action row rather than being hidden lower in the page.

Examples:

- `Update requires new permissions`
- `Cannot enable on current host version`

### 3. Overview

This section explains what the plugin is in product terms. It should contain:

- short description
- what it does
- install source
- last updated time

It should not be overloaded with low-level metadata.

### 4. Safety & Compatibility

This is the most important analytical section of the detail page. It combines the current compatibility, permission, and failure material into a clearer structure.

It should be split into three cards:

- `Compatibility`
- `Permissions`
- `Runtime health`

This lets users immediately distinguish between:

- host-version problems
- permission review problems
- runtime execution problems

### 5. Plugin Panel / Settings

If the plugin exposes schema cards or settings, that UI should be promoted into its own explicit work area instead of appearing like optional leftover content at the bottom.

Suggested section titles:

- `Plugin panel`
- `Plugin settings`

This area is higher priority than technical metadata because it is more likely to contain user-facing interaction.

### 6. Technical Metadata

Technical and package-oriented details move to the bottom of the page, including:

- author
- protocol version
- minimum host version
- maximum host version
- source location
- version history

These details remain available but should be visually weaker than operational content.

## Entry and Navigation Rules

The design assumes two entry paths into detail:

- from homepage `Open`
- from homepage risk or status prompts that deep-link into the relevant detail section

The detail page should support arriving with a clear context, especially for:

- update review
- compatibility review
- permission review
- runtime failure review

## State System

Plugin states should be grouped by priority, not presented as a flat set of labels.

### Normal

- enabled
- disabled
- up to date

### Attention

- update available
- compatibility unknown

### Critical

- incompatible
- permission escalation required
- suspended after failures

The homepage should visually emphasize `Attention` and `Critical` states. `Normal` states should remain readable but low-noise.

## Visual Direction

The plugin redesign must align with the visual language of the rest of the app.

### Approved Visual Constraints

- keep the existing monochrome and low-saturation surface language
- do not introduce a separate plugin-only palette
- use color as functional status signaling, not decorative branding
- derive the marketplace feeling from structure, hierarchy, and component composition

### Color Use Rules

Neutral surfaces remain the primary visual foundation:

- page backgrounds
- cards
- section containers
- hero surfaces

Functional color is reserved for status states such as:

- update available
- incompatible
- permission risk
- failure or suspension

This keeps the page aligned with other AstrBot screens while still making critical states easy to notice.

### Source of Marketplace Feel

The plugin page should feel more like a marketplace because of:

- a stronger hero
- clearer summary hierarchy
- better card composition
- more intentional discovery layout
- stronger filtering and browsing affordances

It should not rely on:

- strong gradient branding
- high-saturation surfaces
- decorative store-like visuals that conflict with the app's current tone

## Content Density Principle

The homepage should be information-dense in a scan-friendly way, not text-heavy in a reading-heavy way.

That means:

- short summaries
- limited status labels
- one dominant card action
- long metadata pushed into detail

This principle is what allows the page to feel like a real plugin library instead of a storage area for all plugin-related data.

## Design Summary

The redesign changes the plugin experience from a mixed workspace into a structured two-level flow:

- homepage for overview, discovery, and action prioritization
- detail page for understanding and operating a single plugin

The homepage becomes `Library-first with market shell`.

The detail page becomes a dedicated operational page instead of an in-place workspace toggle.

The visual system stays aligned with the rest of AstrBot, while the information architecture becomes significantly clearer and more decision-oriented.
