---
name: SYNERA Sales Mobile
description: A light iPhone field workspace with clear blue actions and honest operating state.
colors:
  canvas: "#f6f6f7"
  surface: "#ffffff"
  muted-surface: "#f3f5f7"
  text: "#1f2937"
  muted: "#526071"
  border: "#e0e5eb"
  primary: "#1769c2"
  accent: "#eaf3ff"
  hero-blue: "#1155aa"
  hero-mid: "#69a5de"
  metric-blue: "#dfeeff"
  route-blue: "#f0f7ff"
  danger: "#b42318"
typography:
  body:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, sans-serif"
    fontSize: "17px"
    fontWeight: 400
    lineHeight: "25px"
  title:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, sans-serif"
    fontSize: "17px"
    fontWeight: 600
    lineHeight: "25px"
  headline:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, sans-serif"
    fontSize: "21px"
    fontWeight: 700
    lineHeight: "29px"
  label:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, sans-serif"
    fontSize: "15px"
    fontWeight: 500
    lineHeight: "22px"
  button:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, sans-serif"
    fontSize: "16px"
    fontWeight: 600
  status:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 600
    lineHeight: "21px"
  caption:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, sans-serif"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: "19px"
rounded:
  control: "8px"
  inset: "10px"
  section: "14px"
  active-visit: "15px"
spacing:
  compact: "8px"
  control: "12px"
  group: "16px"
  content: "20px"
  state: "24px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    typography: "{typography.button}"
    rounded: "{rounded.control}"
    padding: "12px 18px"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    typography: "{typography.button}"
    rounded: "{rounded.control}"
    padding: "12px 18px"
  field:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.control}"
    padding: "{spacing.control}"
  search:
    backgroundColor: "{colors.muted-surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.inset}"
    padding: "0px 12px"
  section:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.section}"
  section-blue:
    backgroundColor: "{colors.route-blue}"
    rounded: "{rounded.section}"
  segment-selected:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    rounded: "{rounded.control}"
    padding: "0px 5px"
  status:
    textColor: "{colors.primary}"
    typography: "{typography.status}"
  active-visit:
    backgroundColor: "{colors.route-blue}"
    textColor: "{colors.text}"
    rounded: "{rounded.active-visit}"
  navigation:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
---

# Design System: SYNERA Sales Mobile

## Overview

**Creative North Star: "SYNERA's light field workspace"**

A calm, bright workspace supports sales work at the point of sale. Gray-white canvas, white grouped surfaces, clear blue actions and darker/lighter blue functional panels carry the identity. The fixed Light Mode is a user requirement, including when the device uses dark appearance.

Keep the original SYNERA name and logo. Use the supplied Quân Đào portrait for that local account; retain the existing initials fallback for other accounts. Photography identifies people and products. The approved broad blue greeting that fades into light content is a home-screen expression; its exact composition belongs in the sales-iOS surface brief.

**Key Characteristics:**
- Light neutral canvas with white and pale-blue operational groups.
- System typography, readable amounts and clear action hierarchy.
- Soft section shadows, thin separators and gently rounded controls.
- Server-backed status and restrained motion.

This is an extraction of `src/theme.ts`, `src/components/ui.tsx`, `src/screens/Today.tsx`, `src/navigation.tsx` and the approved product/surface commitments. React Native measurements below are logical layout units; frontmatter uses px strings for interchange. The sidecar's standalone HTML/CSS previews translate native patterns and do not establish new native hover or focus styling. Chromium previews at widths (375px) and (430px) supplied visual evidence. The user separately confirmed opening the app on an iPhone; native visual/accessibility acceptance remains unverified.

## Colors

The palette moves from confident action blue through pale operational tints into a cool, near-white canvas. Frontmatter owns the exact values; the dark object remaining in source is inactive and is not an alternate supported appearance.

### Primary

- **Action Blue** (`primary`): primary buttons, icons, selected tabs, amounts and the active-visit band.
- **Greeting Blue** (`hero-blue`) and **Fade Blue** (`hero-mid`): the approved home gradient, combined with action blue before it reaches the canvas.
- **Clear Blue Tint** (`accent`): section title strips, action-icon wells and pressed list rows.
- **Metric Blue** (`metric-blue`): tonal differentiation in sales metrics.
- **Route Blue** (`route-blue`): blue section variant and active-visit content.

### Secondary

- **Alert Red** (`danger`): actionable errors and alerts; not a decorative accent.

### Neutral

- **Gray-White Canvas** (`canvas`): the overall scrolling background.
- **White Surface** (`surface`): cards, inputs and secondary actions; also on-blue text.
- **Quiet Surface** (`muted-surface`): search and segmented-control tracks.
- **Ink** (`text`): primary readable content.
- **Slate** (`muted`): detail, captions and inactive navigation.
- **Separator Gray** (`border`): control outlines and fine separators.

**The Light Canvas Rule.** Preserve the bright overall canvas. Blue regions serve functional emphasis and the approved greeting rather than replacing the whole screen background.

## Typography

**Display Font:** Native system font, SF on iOS.
**Body Font:** Native system font, SF on iOS; system sans-serif in the web preview.

The source inherits React Native's system font instead of loading a display family. Preserve Vietnamese accents and use semibold/bold emphasis to support scanning. There is no separate mono font; amounts and the elapsed clock use tabular numerals.

### Hierarchy

- **Headline:** shared section heading, with the frontmatter headline role.
- **Title:** list and action titles, with the frontmatter title role.
- **Body:** ordinary reading content, with the frontmatter body role.
- **Label:** field labels; button and status variants have their own extracted roles.
- **Caption:** supporting context, with the frontmatter caption role.

Home additionally uses a greeting name (29 / 37, weight 700), route headings (20, weight 700) and metric values (20, weight 700). These are observed surface styles, not a replacement shared type scale. The elapsed clock uses (22, weight 700) and tabular numerals. Avoid shrinking operational text to force a fixed screenshot composition.

## Layout

Use a vertically scrolling, single-column phone workspace. Shared content has horizontal padding (20); the home content inset is (16), while the greeting uses (22). Shared gaps use the extracted spacing scale; section groups commonly end with a gap (20). Avoid promoting the home's greeting/card overlap into a required layout for every screen.

Respect safe-area insets: the home greeting includes the top inset; the bottom tab bar adds the bottom inset to its base height (70). Its top padding is (6), and bottom padding is the greater of the bottom inset and (8). Keep scroll content clear of persistent navigation. There is no implemented responsive breakpoint or desktop grid to canonize.

Buttons have a minimum height (48). Segmented items and the active-visit continue control have minimum height (44). Search has minimum height (44), standard fields (48), and multiline fields (100). Interactive list rows have minimum height (72); action rows use (68). These describe implemented component targets, not a claim that every screen has passed native touch-target review.

## Elevation & Depth

White sections use a soft neutral shadow, while the promoted active visit uses a slightly stronger blue shadow. Pale fills and separators carry most of the hierarchy. Preserve the distinction between the shadow-casting wrapper and the clipped inner section so the shadow remains visible.

### Shadow Vocabulary

- **Section lift:** `0 3px 14px rgba(27,39,51,0.075)`, from `kit.elevation`.
- **Active-visit lift:** `0 5px 20px rgba(23,105,194,0.15)`, from `Today.activeShell`.

The home greeting gradient uses the source colors and stops (0, 0.55, 0.84, 1). It resolves into the light canvas rather than a separate dark backdrop.

## Shapes

Controls use modest corners; sections use a broader curve. The frontmatter radius roles preserve the existing hierarchy. Search, action-icon wells and segment tracks share the inset radius; the selected segment shares the control radius. Avatars are circular, clipped to their measured size.

Use native hairline separators for rows and navigation boundaries. Buttons and fields use a border (1). Keep source clipping on rounded section interiors and image wells. Do not introduce a new pill, glass or ornamental shape system.

## Components

### Buttons

Direct and legible, with a filled primary action and outlined secondary action. Both use the control radius, minimum height (48), horizontal padding (18) and vertical padding (12). Labels use the button typography role. Secondary borders use separator gray. Pressed opacity is (0.8), disabled opacity (0.55), and disabled controls retain their accessibility state. The source does not define a custom hover/focus treatment.

### Cards / Containers

Shared sections use the section radius and shadow vocabulary. A title strip uses the blue tint, with horizontal padding (12), vertical padding (10) and corner radius (9); its text uses the shared row-title style. The body can be white or route blue. Internal padding belongs to the contained pattern rather than a universal card padding token.

### Inputs / Fields

Labeled fields use white surfaces, separator borders, control corners and padding (12), with body-size input text. Search uses a quiet surface, leading search icon and optional clear action; it has no decorative shadow. Preserve accessibility labels and appropriate keyboard behavior. No custom focused/error/disabled field visual variant is established by the shared source.

### Segmented Controls and Status

The segmented track is quietly tinted, with padding (3); the selected item is white with action-blue semibold text (14). Other items use muted text. Keep the selected accessibility state and the minimum item target (44). Status is a text label, not a filled pill: action blue by default, muted for cancelled status, with the shared status typography.

### Navigation

Use the existing four bottom areas: Today, Stores, Orders and Account, with the app's Vietnamese labels. The white bar uses a thin top separator, action blue for active items and slate for inactive items. Labels use size (12), line height (16), and allow font scaling. Stack headers use system navigation, centered section titles (18, weight 600) and no header shadow.

### Active Visit

This is the implemented operational signature on Today: a darker action-blue header above route-blue detail, store identity, a live elapsed clock and secondary/primary actions. The shell uses its dedicated radius and shadow. Its clock derives from the visit's checked-in timestamp, updates while the app is active and shows an em dash when unavailable.

The entrance is one fade from opacity (0.6) to (1) over (420ms), using `Easing.out(Easing.cubic)`; it is skipped when Reduce Motion is enabled. The clock changes text without a repeating decorative animation.

### Identity and Imagery

Reuse `assets/synera-logo.png` and `assets/quan-dao-avatar.jpg`; preserve their identity and aspect treatment. The supplied portrait applies to the configured local account, not every user. Product thumbnails use existing SKU imagery or the existing cube fallback. Asset provenance belongs alongside those assets; this document grants no replacement-art direction.

## Do's and Don'ts

### Do:

- **Do** keep Light Mode fixed and the overall canvas gray-white.
- **Do** reuse the original SYNERA logo and the supplied portrait for its intended account.
- **Do** use blue fills to distinguish actions and operational groups while retaining light content.
- **Do** preserve readable Vietnamese text, tabular operational numbers and safe-area spacing.
- **Do** render real server values and real visit state, and respect Reduce Motion.
- **Do** keep missing-data, loading, error and retry states explicit.

### Don't:

- **Don't** activate the unused dark palette in response to device appearance.
- **Don't** substitute invented portraits, logos, sales KPIs or elapsed visit values.
- **Don't** add “tạm tính” or technical metadata explanations to the sample home metrics; keep source metadata intact.
- **Don't** turn the home greeting or active-visit composition into a mandatory layout on unrelated screens.
- **Don't** treat Chromium screenshots or opening the app on a phone as native visual/HIG acceptance.
