# Quiet Productivity Design System

## 1. Product direction

The Markdown app should feel like a calm, modern writing tool rather than a developer demo or a traditional Android utility. The interface should be visually quiet so the user's content remains the strongest element.

**Design keywords:** minimal, calm, precise, editorial, professional.

## 2. Core principles

1. **Content first** — content receives the largest visual area and strongest contrast.
2. **One primary action per context** — avoid crowded toolbars and duplicated controls.
3. **Progressive disclosure** — advanced functions appear only when relevant.
4. **Soft structure** — use spacing and subtle surfaces before borders or shadows.
5. **Consistent feedback** — saving, selection and mode changes should be visible but quiet.

## 3. Color tokens

| Token | Value | Usage |
|---|---|---|
| `qp_background` | `#F7F7F5` | App page background |
| `qp_surface` | `#FFFFFF` | Raised and control surfaces |
| `qp_surface_secondary` | `#F1F1EE` | Secondary controls |
| `qp_text_primary` | `#1C1C1A` | Primary text |
| `qp_text_secondary` | `#6B6B66` | Supporting text |
| `qp_text_tertiary` | `#A0A09A` | Metadata and hints |
| `qp_brand` | `#2F6B5F` | Focused states and links |
| `qp_divider` | `#E7E7E1` | Low-emphasis separation |

The brand color must not be used as a large decorative background by default.

## 4. Typography

- UI text: system sans serif / Noto Sans SC where available.
- Markdown body: system sans serif with generous line height.
- Code: platform monospace.

Recommended mobile scale:

- Document title: 17sp bold
- H1: approximately 32sp equivalent
- H2: approximately 25sp equivalent
- H3: approximately 20sp equivalent
- Body: 16–17sp
- Metadata: 12–13sp

Reading content should use a line height of roughly 1.75–1.85.

## 5. Spacing and shape

Base spacing rhythm: `4 / 8 / 12 / 16 / 24 / 32dp`.

Corner radius:

- Small controls: 10dp
- Mode containers: 14dp
- Content cards: 16dp
- Large surfaces: 20dp

Avoid heavy shadows. Prefer background contrast and whitespace.

## 6. Icon system

All product icons must follow one visual family:

- Outline or rounded-outline style
- Similar stroke weight
- No emoji used as UI icons
- Touch target at least 44dp

Icon resources may be sourced from Iconfont, but only after checking license and consolidating them into a single project icon family. Do not mix unrelated Material, Font Awesome and Iconfont styles in the same navigation or toolbar.

Suggested search terms on Iconfont: `线性`, `圆角`, `极简`, `outline`, `minimal`.

## 7. Editor behavior

The editor is a **Focus Editor**:

- Document title and save state remain quiet at the top.
- Save automatically after a short debounce and on lifecycle transitions.
- Markdown tools are horizontally scrollable and secondary to writing.
- Preview is a mode switch, not a separate product with a different visual language.

## 8. Markdown preview

Preview should resemble a comfortable article reader:

- Generous side padding.
- Clear heading hierarchy.
- Body line height around 1.8.
- Inline code uses a light neutral surface.
- Code blocks use a dedicated high-contrast surface.
- Quotes use a subtle left accent rule.
- Tables and images must not overflow the viewport.

## 9. Commercial UX

Commercial prompts must appear at the moment a premium capability is requested, not as persistent interruptions on the writing surface. The editor and basic local document experience should remain calm and usable without promotional banners.

## 10. Implementation rule

Every new screen or component must first reuse these tokens. Do not hardcode arbitrary colors, radii or spacing when an existing design token can be used.
