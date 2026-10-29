# Emre Gallery — Primary Design System

This skill defines the core design ethos and strict implementation standards for Emre Gallery. All UI generation, screen refactoring, and component authoring must adhere to these four foundational pillars with top priority.

---

## 1. Monochrome

> **"Build the palette from neutrals. Whites, grays, and near-blacks carry the entire interface. Color is not decoration — so accent is rare."**

*   **Palette Foundations:**
    *   Construct all backgrounds, surfaces, text, icons, and controls from pure neutral tones:
        *   **Light Theme:** Pure white (`#FFFFFF`), whisper grays (`#F7F7F7`, `#F0F0F0`), mid-tone neutrals (`#8E8E93`, `#636366`), and deep near-blacks (`#1C1C1E`, `#000000`).
        *   **Dark Theme:** True OLED/near-black (`#000000`, `#121212`, `#181818`), dark graphite (`#242424`, `#2C2C2E`), neutral muted grays (`#8E8E93`, `#AEAEB2`), and crisp off-white (`#F2F2F7`, `#FFFFFF`).
*   **Restraint with Color:**
    *   Color must never be applied for purely aesthetic or decorative purposes.
    *   No vibrant purple, pink, teal, or blue gradients on cards, containers, or buttons.
    *   Accent colors are strictly functional and rare:
        *   System error / destructive confirmations (subtle crimson/red).
        *   Camera / recording live indicator (subtle amber/red dot).
    *   All primary actions, buttons, toggles, chips, and icons use monochrome values (black on white in light theme; white on dark gray in dark theme).

---

## 2. Low Clutter

> **"Every element on screen must justify its presence. No decorative cards, no labels that repeat what a control already says, no visual structure that exists for its own sake. When in doubt, leave it out. Density should come from content, not chrome."**

*   **No Decorative Cards or Containers:**
    *   Avoid wrapping plain text or simple controls in nested, bordered, or shaded cards if plain spacing suffices.
    *   Do not stack cards inside cards.
*   **Zero Redundant Labels:**
    *   If a toggle or control already indicates its function (e.g. a Sun/Moon toggle or a slider with an icon and number), do not add explanatory paragraphs or subtitles restating what it does.
    *   Remove filler descriptions (e.g. avoid *"Customize app appearance"*, *"Adjust this slider to change setting"*, *"Configure options below"*).
*   **Content-Driven Density:**
    *   The gallery's primary hero is the user's media (photos, videos, albums, faces).
    *   Keep application chrome, headers, pills, and navigation bars minimal, unobtrusive, and lightweight so content commands the screen.
    *   When in doubt, leave it out.

---

## 3. Calm Hierarchy

> **"Establish visual order through spacing and type weight, not borders or backgrounds. Let breathing room do the work that dividers would. The screen should feel composed at a glance — the eye finds what it needs without searching."**

*   **Spacing Over Dividers:**
    *   Avoid hard dividing lines (`HorizontalDivider`), stark borders, and patchwork background colors to separate sections.
    *   Use generous, intentional padding (e.g. 16.dp, 24.dp, 32.dp) and vertical rhythm to naturally group elements.
*   **Typographic Clarity:**
    *   Differentiate section headings, primary titles, and secondary metadata through font weights (`FontWeight.Bold`, `FontWeight.SemiBold`, `FontWeight.Normal`) and subtle text contrast (`colorScheme.onSurface` vs `colorScheme.onSurfaceVariant`), rather than large background badges.
*   **At-a-Glance Scannability:**
    *   Maintain consistent horizontal alignment and baseline alignment.
    *   Sliders, toggles, and metadata should align cleanly across lists so the user's eye scans vertically with zero friction.

---

## 4. Physical Connection

> **"Gestures, drags, and swipes must feel like they're touching real things. Motion should be a direct response to input, not a choreographed animation that plays afterward. Transitions communicate where you came from and where you're going."**

*   **Direct Manipulation:**
    *   Drags, pinches, and scrolls track finger position 1:1 in real time.
    *   Predictive back gestures (`PredictiveBackHandler`) scale and slide screens directly with the user's touch progress (`progress.collect { backEvent -> ... }`).
    *   Pinch-to-zoom on grids dynamically adjusts column count based on continuous distance.
*   **Immediate Tactile Feedback:**
    *   Touchable surfaces and buttons must provide instant visual feedback on press.
    *   Never suppress tap responsiveness; interactive elements respond instantaneously.
*   **Spatial Continuity:**
    *   Transitions must communicate origin and destination:
        *   Drilling down into an album slides forward; popping back slides reverse.
        *   Opening photo viewer scales smoothly outward from the touched photo, maintaining spatial context.
        *   Subscreens glide laterally or fade smoothly without disconnected jumping or jarring delays.
