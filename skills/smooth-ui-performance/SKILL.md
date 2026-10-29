---
name: "smooth-ui-performance"
description: >-
  Instructs architectural patterns for maintaining silky-smooth 60/90/120 FPS performance on low-end hardware and high refresh rate panels.
  Covers UI thread decoupling, hardware acceleration, GPU compositing, view recycling, aggressive multi-tier downsampling,
  two-pass thumbnail loading, bitmap pooling with inBitmap, decoupled background metadata queries, and 120 FPS perceptual judder fixes
  (sub-pixel snapping, graphicsLayer vs offset, touch input jitter, retinal tracking disruption, and crossfade/aspect ratio constraints in Jetpack Compose).
---

# Smooth UI Performance: Low-End Hardware Optimization & Thread Decoupling

Modern apps feel silky smooth on low-end hardware primarily because modern UI frameworks decouple what you **see** from what the CPU is **calculating**. A device doesn't need raw computing horsepower to hit 60 or 90 frames per second; it just needs an architecture designed to prevent the main UI thread from choking.

---

### 1. Separation of Concerns (Offloading the UI Thread)

In older application architectures, calculating data, fetching a network payload, parsing JSON, and drawing pixels frequently competed on the same CPU thread. When a computation spiked, the frame rate plummeted.

* **Dedicated Render Threads:** Operating systems and modern UI engines (Android’s RenderThread, iOS’s Core Animation, Flutter’s raster thread, React Native’s Hermes + Fabric architecture) run visual rendering independently.
* **Background Asynchrony:** Heavy tasks (database I/O, network calls, sorting large datasets) are offloaded to asynchronous background worker threads (like Kotlin Coroutines or Grand Central Dispatch). Even if a budget CPU takes 300 ms to parse data, the scrolling surface keeps rendering frames at full refresh speed without dropping a beat.

### 2. Hardware Acceleration by Default

Mobile CPUs might be weak on low-end devices, but their integrated GPUs and display processors (DSPs) are specialized for rasterization, matrix transformations, and blending.

* **GPU Draw Commands:** Instead of using software rendering on the CPU to redraw layouts pixel by pixel, modern toolkits translate UI elements into GPU display lists and render passes.
* **Compositor Layers:** Common animations—like scrolling a list, sliding a drawer, or fading an element—are GPU texture transformations. The CPU merely issues a single matrix instruction (e.g., "translate Y by -20px"), leaving the GPU hardware compositor to glide the texture across the screen effortlessly.

### 3. Smart Virtualization and View Recycling

Well-built modern applications do not render everything on the screen at once.

* **Viewport Recycling:** Components like `RecyclerView` (Android) or `LazyColumn` (Jetpack Compose) only instantiate and keep in memory the 6 to 10 items actively visible in the viewport.
* As an item scrolls off the top, its underlying memory container is immediately bound to new incoming data from the bottom. The CPU never handles 1,000 items simultaneously; it recycles a tiny pool of views.

### 4. Ahead-of-Time (AOT) Compilation and Profile Optimization

Older mobile apps often relied on heavy runtime Just-in-Time (JIT) compilation or unoptimized bytecode interpretation, causing stutter during first-time execution.

* **Baseline Profiles:** Modern app packaging (such as Android ART Profiles / Baseline Profiles) compiles the exact critical user journeys (app startup, opening feeds, list scrolling) into lean native machine code ahead of time.
* Compilers strip dead code, inline small methods, and organize memory layouts specifically to avoid CPU instruction-cache misses.

### 5. Optimistic UI and Perceived Performance

Engineers frequently trade raw speed for psychological smoothness:

* **Instant Visual Feedback:** When you tap a "Like" button or submit a form, modern apps trigger haptic feedback and animate the button immediately. The app doesn't wait for the server roundtrip or the background write; it assumes success and animates up front.
* **Shimmers and Skeletons:** Instead of stalling the UI with a blocking loading screen, the app immediately paints lightweight layout skeletons so the viewport feels instantly responsive.

---

When an app stutters on a low-end phone, it is almost never because the CPU lacks the raw power to move 2D elements around. It stutters because poorly written code blocked the main thread. When developers respect thread boundaries and lean on GPU compositing, even an entry-level budget processor feels effortless.

---

## Gallery Application Domain: Low-End Hardware & Thread Architecture

Gallery apps face a uniquely brutal performance challenge: decoding hundreds of heavy JPEGs/HEICs, querying tens of thousands of MediaStore records, and maintaining a constant 60 or 90 Hz scroll—all while running on low-tier memory buses and weak CPU cores.

When a gallery app feels liquid-smooth on budget hardware, it is relying on specific architectural patterns that avoid touching raw image files on the main thread.

---

### 1. View Virtualization and Cell Recycling

A gallery grid with 20,000 photos never actually has 20,000 views in memory.

* **Reused Layout Containers:** Whether using Android’s `RecyclerView` or modern declarative grids, the system only keeps roughly 20 to 30 view holders in RAM (just enough to fill the active screen plus a 1-screen buffer above and below).
* **Flat View Hierarchies:** Well-tuned gallery cells do not use nested wrappers (`CardView` -> `FrameLayout` -> `ImageView`). They use flat, single-layer views to minimize layout measure/layout passes during high-speed flings.

### 2. Aggressive Multi-Tier Downsampling

Decoding a full-resolution 12MP or 48MP camera photo into an uncompressed bitmap requires between 48 MB and 190 MB of RAM ($Width \times Height \times 4\text{ bytes}$). Attempting to decode even three full photos at once on a budget device will trigger an immediate Out-Of-Memory (OOM) crash or intense garbage collection pauses.

* **Sub-sampling via `inSampleSize`:** Efficient image engines (like Glide, Coil, or custom decoders) read only image headers first via `inJustDecodeBounds = true` to get dimensions. They then compute a power-of-two divisor to decode *only* the pixels needed for the thumbnail (e.g., downsampling a 4000×3000 image to a 256×256 thumbnail directly during stream decoding).
* **Direct Hardware Bitmaps (`Bitmap.Config.HARDWARE`):** On modern Android versions, decoded pixels are loaded directly into GPU graphic buffers (`GraphicBuffer` / `AHardwareBuffer`). This skips keeping duplicate byte arrays in Java/Kotlin heap memory, eliminating Garbage Collection (GC) pauses entirely.

### 3. The Two-Pass Thumbnail Strategy

Smooth gallery grids do not load the actual photos while you are fast-scrolling.

1. **System Micro-Thumbnails First:** Instead of reading the actual image file on storage, the app immediately fetches the tiny, pre-cached micro-thumbnail generated by the OS media provider (or stored in an embedded database).
2. **Progressive High-Res Crossfade:** If the user stops or slows down scrolling on a specific photo, a background thread decodes the higher-resolution thumbnail and cleanly crossfades it in.
3. **Cancellation of Off-Screen Requests:** If a user flings the grid rapidly, pending decode jobs for items that fly past the viewport are canceled in microseconds before the CPU spends cycles decompressing JPEG data that will never be seen.

### 4. Direct Disk Caches and Bitmap Pooling

Creating and destroying bitmap objects at 60 frames per second floods the Android runtime allocator and triggers the Garbage Collector, causing noticeable micro-stutters (jank).

* **Bitmap Pool (`inBitmap`):** Instead of allocating new heap space for every thumbnail, image loaders maintain an in-memory pool of recycled bitmaps. A recycled bitmap container is reused to decode the next incoming thumbnail of matching dimensions, resulting in near-zero memory allocation spikes while scrolling.
* **Specialized Cache (DiskLRU / SQLite Blob / Memory-Mapped I/O):** High-performance galleries write extracted thumbnails to a packed, contiguous local cache (often using memory-mapped files or high-speed KV databases). Reading 100 contiguous 10 KB blocks sequentially is dramatically faster on cheap eMMC storage than making 100 separate random-access file open/read calls across the flash drive.

### 5. Decoupled Metadata Queries

Fetching photo metadata (dates, geotags, filenames, camera info) directly from disk EXIF headers in real time would instantly freeze any CPU.

* **Batch Cursor Loading:** Modern galleries query the indexed database (`MediaStore` / SQLite) in chunks on background dispatcher threads, caching only lightweight identifier models (ID, timestamp, path, rotation).
* **Sticky Header Computations Off-Thread:** Calculating date dividers ("Yesterday", "October 2026") is done as a background pre-processing step. The UI grid binds pre-calculated grouping indices rather than parsing date strings on the fly inside the scroll loop.

---

### Key Architectural Flow

| Operation | Main / UI Thread | Worker Threads (CPU) | GPU / Compositor |
| --- | --- | --- | --- |
| **Grid Scroll** | Dispatches matrix translation | Idle | Composites cached surface textures |
| **Data Fetching** | Reads fast in-memory list pointer | Queries cursor & batches IDs | Idle |
| **Image Decoding** | Idle | Streams, downsamples & binds to `inBitmap` | Idle |
| **Displaying** | Issues draw command to surface | Idle | Renders hardware bitmap to display layer |

---

# 120 FPS Perceptual Judder, Spatial Consistency & High Refresh Rate Panels

If an app is hitting a steady 120 frames per second on a 120 Hz panel and still feels stuttery, the problem is no longer frame delivery—it is **spatial inconsistency**, **input-to-display desynchronization**, or **display hardware behavior**. The eye registers motion smoothness not just from refresh rate, but from uniform spatial displacement between frames.

---

### 1. Touch Input Jitter & Asymmetric Spatial Delta ($dY$)

At 120 Hz, a new frame is presented every **8.33 milliseconds**. If an app moves content strictly based on raw touch digitizer events, input timing variations will cause visual judder:

- **Non-uniform displacements:** If your finger moves at a constant speed, the list should ideally move by an exact distance each frame (e.g., `10px, 10px, 10px, 10px`).
- **Input poll desync:** If the touch sensor (even a 240 Hz or 360 Hz digitizer) delivers events at slight timing offsets relative to the VSYNC boundary, the captured scroll offsets might calculate to `14px, 6px, 15px, 5px`.
- Even though the GPU renders 120 frames with zero dropped frames, the physical motion on screen oscillates between speeding up and slowing down. The human retina tracks the speed anomaly and interprets it as a stutter.
- **The fix:** Modern physics engines use predictive motion filtering and spline interpolation (like Android's `VelocityTracker` and gesture interpolators) to smooth out the positional delta curve across VSYNC intervals instead of binding directly to raw touch packets.

---

### 2. Sub-Pixel Snapping and Integer Truncation

When the scroll position is calculated as a floating-point number (e.g., scrolling at 1.4 pixels per frame):

- If the layout or canvas engine truncates or rounds positions to whole integers before drawing (`Canvas.translate(x, y.toInt())` or `Modifier.offset { IntOffset(...) }`), the frame translations will render as:

$$1\text{px}, 1\text{px}, 2\text{px}, 1\text{px}, 2\text{px}$$

- This micro-jumping between 1-pixel and 2-pixel movements at high speeds creates a visible, high-frequency crawl or vibrating jitter on sharp edges and text, completely destroying the sensation of fluid motion.

---

### 3. Asymmetric Image Pop-In (Retinal Tracking Disruption)

When you scroll, your eyes do not look at the phone statically; they actively track specific elements across the screen (smooth pursuit eye movement).

- If the list itself is gliding at a flawless 120 fps, but image thumbnails swap from a low-res blur/placeholder to a high-res bitmap mid-flight, the visual feature your eye is tracking suddenly alters in contrast, sharpness, or edge definition.
- This abrupt visual jump halts smooth retinal pursuit for a split second, tricking the brain into feeling a hitch even when the animation frame rate never dipped below 120 fps.

---

## Jetpack Compose & Modern Android Implementation: 120 FPS Polish

In Jetpack Compose and Modern Android, maintaining an actual 120 FPS render pipeline while suffering from perceptual judder is typically caused by three specific failure points:

---

### 1. Fixing Input Jitter & Pointer Desync

In Compose, if you read pointer movements using raw `pointerInput` coordinates (`pointerInputChange.positionChange()`) and feed those directly into a layout state, you bypass the platform's native resampler and introduce digitizer phase-jitter.

#### A. Do Not Drive Custom Scrolling via Raw Delta Accumulation

If building scrollable containers, avoid manually accumulating raw drag offsets into a state variable:

* **Use `scrollable` or `draggable` modifiers:** Compose’s built-in `Modifier.scrollable(...)` routes through `ScrollableState`, which properly consumes consumed deltas and works in tandem with the platform's fling physics (`FlingBehavior`).
* **Velocity Calculation Strategy:** When handling custom gestures, never calculate velocity manually with `delta / deltaTime`. Always delegate to `VelocityTracker`:

```kotlin
// In custom pointer input
val velocityTracker = remember { VelocityTracker() }

Modifier.pointerInput(Unit) {
    detectDragGestures(
        onDrag = { change, dragAmount ->
            change.consume()
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            // Update translation/offset
        },
        onDragEnd = {
            val velocity = velocityTracker.calculateVelocity()
            // Launch fling animation using platform spline physics
        }
    )
}
```

#### B. Align Custom Canvas Gestures with `withFrameNanos`

If you are doing continuous position updates or running an interactive canvas simulation, update position state strictly inside a `withFrameNanos` / `withFrameMillis` loop (which hooks directly into Android's `Choreographer` VSYNC signal), rather than updating state immediately on every raw pointer packet.

---

### 2. Fixing Sub-Pixel Crawl & Integer Truncation

Compose performs layout on integer pixel grids (`IntOffset`, `IntSize`, `Constraints`). Passing fractional scroll offsets into layout modifiers causes continuous micro-jumps between integer boundaries.

#### A. Route Offsets Through `graphicsLayer` (Draw Phase), Not Layout Modifiers

* **The Anti-Pattern (Causes Sub-Pixel Stutter):**
```kotlin
// BAD: Truncates float to Int, forces re-measurement/re-layout every frame
Modifier.offset { IntOffset(0, scrollY.roundToInt()) }
```

* **The Fix (Sub-Pixel Antialiasing via Skia/RenderThread):**
```kotlin
// GOOD: Preserves float precision, renders on GPU matrix, skips Recomposition & Layout
Modifier.graphicsLayer {
    translationY = scrollY // scrollY is a Float
}
```

Using `graphicsLayer` delegates the transformation directly to Skia / Vulkan draw matrices. The edges and text are antialiased against physical display subpixels without rounding to discrete integer coordinates.

#### B. Decouple Recomposition from Frame Invalidation

When a scroll coordinate changes at 120 Hz, triggering a Compose recomposition loop can introduce small frame-pacing anomalies. Ensure continuous fractional values are passed as lambdas directly to drawing phases:

```kotlin
// BAD: Recomposes on every 0.1f delta
val scrollOffset by scrollState

// GOOD: Lambda reads state during Draw phase only
Modifier.graphicsLayer {
    translationY = scrollState.value
}
```

---

### 3. Fixing Asymmetric Image Pop-In (Smooth Pursuit Preservation)

In Jetpack Compose, asynchronous image loaders (like **Coil**) often swap loaded bitmaps into `AsyncImage` synchronously during active scrolling. When a high-res bitmap replaces a blurry or blank placeholder, the eye tracking the element across 120 Hz loses fixation.

#### A. Throttle Image Loading During Fast Flings

Pause or restrict Coil from decoding and dispatching new high-res bitmaps while the list is flinging rapidly.

Using `LazyListState`:

```kotlin
val listState = rememberLazyListState()
val isFastScrolling by remember {
    derivedStateOf {
        // Suppress image decode transitions if scroll is active and velocity is high
        listState.isScrollInProgress
    }
}

// Pass state to image loader or conditionally defer high-res loading
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(imageUrl)
        // If scrolling fast, keep low-res cache or defer memory decode
        .crossfade(enable = true)
        .crossfade(150) // Smooth alpha ramp prevents ocular shock
        .build(),
    contentDescription = null,
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f) // STRICT: Lock layout geometry permanently
)
```

#### B. Absolute Aspect Ratio Enforcement

Never allow an image component to load with dynamic wrapping (`wrapContentHeight()` or missing aspect ratios):

* If the incoming bitmap resolves with dimensions differing by even a fraction of a DP, it triggers an instant layout pass that shifts neighboring items by a discrete pixel delta.
* Always define an explicit `Modifier.aspectRatio(...)` or fixed `.size(...)` on the image container so the geometric layout remains completely immutable before, during, and after the bitmap renders.

---

### Summary Checklist for Compose at 120 Hz

1. **Translations:** Replace `Modifier.offset { IntOffset(...) }` with `Modifier.graphicsLayer { translationY = ... }`.
2. **Phase Isolation:** Keep scroll offset state reads out of the Composition block; defer them to the Draw phase (`graphicsLayer` lambda).
3. **Fling & Inertia:** Use Compose's default `ScrollableDefaults.flingBehavior()` or pass pointer events through `VelocityTracker.calculateVelocity()`.
4. **Bitmap Swaps:** Always specify `crossfade(100..150)` and strict `aspectRatio` on `AsyncImage` so visual edges do not pop instantly during foveal pursuit.
