# Design System Master File

> **LOGIC:** When building a specific page, first check `design-system/pages/[page-name].md`.
> If that file exists, its rules **override** this Master file.
> If not, strictly follow the rules below.

---

**Project:** Digital Storefront
**Generated:** 2026-09-17 09:53:55
**Category:** Financial Dashboard
**Design Dials:** Variance 4/10 (Balanced / Modern) | Motion 3/10 (Subtle) | Density 8/10 (Dense / Dashboard)

---

## Global Rules

### Color Palette

| Role | Hex | CSS Variable |
|------|-----|--------------|
| Primary | `#2563EB` | `--color-primary` |
| On Primary | `#FFFFFF` | `--color-on-primary` |
| Money | `#F59E0B` | `--color-money` |
| Secondary | `#737373` | `--color-secondary` |
| Accent/CTA | `#2563EB` | `--color-accent` |
| Info | `#38BDF8` | `--color-info` |
| Background light | `#FAFAFA` | `--color-background-light` |
| Background dark | `#171717` | `--color-background-dark` |
| Foreground light | `#0A0A0A` | `--color-foreground-light` |
| Foreground dark | `#FAFAFA` | `--color-foreground-dark` |
| Surface dark | `#000000` | `--color-surface-dark` |
| Border | `#404040` | `--color-border` |
| Destructive | `#EF4444` | `--color-destructive` |
| Ring | `#737373` | `--color-ring` |

**Color Notes:** Preserve Vercel Commerce's neutral light/dark surfaces and blue actions. Amber is reserved for VND balance, price emphasis, and low-stock status.

### Typography

- **Heading Font:** Geist Sans
- **Body Font:** Geist Sans
- **Mood:** Vercel Commerce, neutral, product-first, trustworthy, precise
- **Package:** `geist`

Use `GeistSans.variable` from `geist/font/sans`; do not load a second web font.

### Spacing Variables

*Density: 8/10 — Dense / Dashboard*

| Token | Value | Usage |
|-------|-------|-------|
| `--space-xs` | `2px` / `0.125rem` | Tight gaps |
| `--space-sm` | `4px` / `0.25rem` | Icon gaps, inline spacing |
| `--space-md` | `8px` / `0.5rem` | Standard padding |
| `--space-lg` | `12px` / `0.75rem` | Section padding |
| `--space-xl` | `16px` / `1rem` | Large gaps |
| `--space-2xl` | `24px` / `1.5rem` | Section margins |
| `--space-3xl` | `32px` / `2rem` | Hero padding |

### Shadow Depths

| Level | Value | Usage |
|-------|-------|-------|
| `--shadow-sm` | `0 1px 2px rgba(0,0,0,0.05)` | Subtle lift |
| `--shadow-md` | `0 4px 6px rgba(0,0,0,0.1)` | Cards, buttons |
| `--shadow-lg` | `0 10px 15px rgba(0,0,0,0.1)` | Modals, dropdowns |
| `--shadow-xl` | `0 20px 25px rgba(0,0,0,0.15)` | Hero images, featured cards |

---

## Component Specs

### Buttons

```css
/* Primary Button */
.btn-primary {
  background: #2563EB;
  color: #FFFFFF;
  padding: 10px 16px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 200ms ease;
  cursor: pointer;
}

.btn-primary:hover {
  opacity: 1;
}

/* Secondary Button */
.btn-secondary {
  background: transparent;
  color: currentColor;
  border: 1px solid #D4D4D4;
  padding: 10px 16px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 200ms ease;
  cursor: pointer;
}
```

### Cards

```css
.card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px;
  transition: border-color 200ms ease;
}

.card:hover {
  border-color: #475569;
}
```

### Inputs

```css
.input {
  background: var(--color-surface);
  color: currentColor;
  padding: 12px 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 16px;
  transition: border-color 200ms ease;
}

.input:focus {
  border-color: #737373;
  outline: none;
  box-shadow: 0 0 0 2px rgb(115 115 115 / 0.35);
}
```

### Modals

```css
.modal-overlay {
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}

.modal {
  background: var(--color-surface);
  color: currentColor;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 24px;
  box-shadow: var(--shadow-xl);
  max-width: 500px;
  width: 90%;
}
```

---

## Style Guidelines

**Style:** Adapted Vercel Commerce Storefront

**Keywords:** neutral light/dark, product-first, responsive tiles, compact list option, clear hierarchy, restrained motion

**Best For:** Digital-goods catalog, wallet, transaction history and operations dashboard

**Key Effects:** 150-200ms color and opacity transitions, skeleton loading, no decorative scroll animation, no glassmorphism, no layout-shifting hover.

### Page Pattern

**Pattern Name:** Vercel Product Grid + Compact List

- **Conversion Strategy:** Use Vercel Commerce featured grid, carousel and product gallery, then let customers switch to a compact list for price/stock comparison; confirm purchase with wallet balance before and after.
- **CTA Placement:** One clear buy button per product row; wallet top-up is always reachable from the header.
- **Section Order:** Three-zone navbar, featured products, category/popular carousel, catalog grid/list, trust/support footer.

---

## Motion

- Use 150-200ms transitions for hover, focus, dialog and copy feedback.
- Use skeletons for loading; do not hide catalog rows behind scroll-reveal animation.
- Respect `prefers-reduced-motion` and avoid adding an animation dependency in the first release.

---

## Anti-Patterns (Do NOT Use)

- ❌ Replacing Vercel's neutral visual language with a generic fintech dashboard
- ❌ Decorative gradients and glassmorphism
- ❌ Physical-commerce shipping, tax, quantity and Shopify variant UI
- ❌ Slow rendering

### Additional Forbidden Patterns

- ❌ **Emojis as icons** — Use SVG icons (Heroicons, Lucide, Simple Icons)
- ❌ **Missing cursor:pointer** — All clickable elements must have cursor:pointer
- ❌ **Layout-shifting hovers** — Avoid scale transforms that shift layout
- ❌ **Low contrast text** — Maintain 4.5:1 minimum contrast ratio
- ❌ **Instant state changes** — Always use transitions (150-300ms)
- ❌ **Invisible focus states** — Focus states must be visible for a11y

---

## Pre-Delivery Checklist

Before delivering any UI code, verify:

- [ ] No emojis used as icons (use SVG instead)
- [ ] All icons from consistent icon set (Heroicons/Lucide)
- [ ] `cursor-pointer` on all clickable elements
- [ ] Hover states with smooth transitions (150-300ms)
- [ ] Dark mode: text contrast 4.5:1 minimum
- [ ] Focus states visible for keyboard navigation
- [ ] `prefers-reduced-motion` respected
- [ ] Responsive: 375px, 768px, 1024px, 1440px
- [ ] No content hidden behind fixed navbars
- [ ] No horizontal scroll on mobile
