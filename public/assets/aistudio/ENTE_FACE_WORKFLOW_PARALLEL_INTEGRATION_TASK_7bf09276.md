# Implementation Task: Add an Ente-Style Face Workflow as a Second, Isolated Engine in Emre Gallery

**Task ID:** `EMRE-FACE-ENTE-PARALLEL-001`  
**Target repository:** `amresalehin/emre-gallery-draft`  
**Target in-repository path:** `docs/tasks/ENTE_FACE_WORKFLOW_PARALLEL_INTEGRATION.md`  
**Priority:** High  
**Status:** Ready for implementation  
**Base inspected:** Uploaded `emre-gallery-draft-commit-377--removed-android-native-face-engine-needs-check.zip`  
**Primary rule:** Add a second complete face-recognition workflow alongside the existing `buffalo_l` workflow. Do not replace, weaken, silently reroute, or delete the existing engine.

---

## 0. Instructions to the coding agent

You are implementing this task in the existing Android/Kotlin/Jetpack Compose repository. Work from the repository's actual source; do not assume that this task file perfectly describes files changed after the inspected archive.

1. **Inspect before editing.** Re-open every listed source file and trace callers before changing a public API.
2. **Implement; do not stop after writing a design.** Work through the phases and acceptance criteria in this file. Keep the diff focused on the face workflow.
3. **Do not ask the user to make routine implementation decisions.** Use the defaults and guardrails in this file. If an essential upstream model/license fact cannot be verified, isolate that blocker, avoid shipping the unverified artifact, and continue with the parts that can safely be implemented.
4. **Do not invent upstream details.** Verify the model files, tensor dimensions, tensor layout, preprocessing, landmark order, output decoding, alignment transform, thresholds, and license from upstream source/model metadata. An architecture blog is not a substitute for checking the actual artifact and source.
5. **Keep work auditable.** Implement in small phases, run targeted tests after each phase, and finish with a file-change list, tests run, tests blocked, upstream sources/licenses checked, and remaining limitations.
6. **No drive-by refactors.** Do not redesign unrelated gallery screens, the viewer gesture system, map, OCR, albums, permissions, navigation, or application identity.

### What “in parallel” means in this task

“Parallel” means **two independently selectable, independently indexable, independently clustered engines that can analyse the same photos and whose data cannot contaminate each other**. It does not require two neural-network sessions to run simultaneously on a phone.

The default execution policy must be **one heavy face-indexing job at a time** to protect RAM, CPU, thermals, and battery. The user must be able to index with Buffalo_L, index with the new Ente-style pipeline, and compare their results without reusing one engine's embeddings or identities for the other. Do not start both engines' inference loops concurrently unless a later, explicit resource-management design safely supports it.

---

## 1. Product objective

Add a second local face-recognition workflow based on the publicly documented Ente Photos face pipeline, with these major stages:

1. Full-image face detection using the documented **YOLO5Face small** variant, including face landmarks where the selected model supports them.
2. Candidate filtering and non-maximum suppression (NMS).
3. Geometric face alignment using the verified landmark convention and similarity-transform implementation.
4. Face embedding inference using the documented **MobileFaceNet** model.
5. Face quality signals, including the documented blur and positioning/pose-related metrics, where the actual available source/artifact supports them.
6. Engine-specific local indexing with explicit model/version provenance.
7. A distinct **linear incremental clustering strategy** that can incorporate user corrections and does not simply reuse the existing Buffalo_L centroid-assignment path while labelling it “Ente”.
8. Engine-scoped people management and correction operations: name, ignore/hide, merge, de-merge/split, remove an incorrect face from a person, and select a cover where supported by the current UI/data model.
9. Separate progress, model status, errors, resume/retry behaviour, and results in the existing indexing/People experience.
10. A safe side-by-side comparison mechanism for the two engines.

All photo decoding, detection, alignment, embedding, quality calculation, and clustering must happen **on device**. A network connection may be used only to fetch a model after its exact source, checksum/integrity mechanism, and licensing terms have been verified. The app must never upload photographs, face crops, landmarks, embeddings, or face indexes to a third-party service as part of this task.

### Definition of done

The app exposes two engine identities, `buffalo_l` and a newly defined stable Ente-style engine ID. Each can index the same URI independently, each has its own model version and own people/cluster namespace, each can be run/resumed/retried/reset without modifying the other, and each has a testable pipeline. Existing Buffalo_L behaviour and stored data remain working.

---

## 2. Non-negotiable constraints

### 2.1 Preserve the current engine

- Keep `BuffaloLFaceEngine` and its existing engine ID `buffalo_l`.
- Keep its existing model/version identity (`w600k_r50`) and 512-dimensional embedding contract unless a separate pre-existing bug must be reported; do not opportunistically alter it in this task.
- Do not delete or rewrite existing Buffalo_L models, embeddings, face rows, clusters, person names, hidden-state flags, feedback, review suggestions, settings, or indexed-media checkpoints.
- Do not change which engine existing screens use by default unless this task explicitly adds a user-controlled engine selection. Existing screens must continue to behave as Buffalo_L screens when no new engine is selected.
- Do not silently fall back from the new pipeline to Buffalo_L, ML Kit, an Android platform face API, or another engine on failure. Show a specific error for the selected engine.

### 2.2 No cross-engine data reuse

- Never compare embeddings produced by different model families as if they were in the same embedding space.
- Never put faces from both engines into the same clustering calculation.
- Never show a person's name or merge a person's identity across engines automatically.
- Never reset all face data when the user asks to rescan one engine.
- All mutations (rename, merge, split, hide/ignore, set cover, remove face, accept/reject suggestion, reset/recluster) must be scoped to the selected `engine_id`.

### 2.3 Resource and reliability constraints

- Reuse the existing ONNX Runtime Android dependency unless the current verified model requires a compatible change. Avoid adding a second inference runtime or a large framework without evidence it is needed.
- Keep model sessions alive across a batch; do not construct and destroy sessions for every photo.
- Close all ONNX sessions on all success, cancellation, and exception paths.
- Bound bitmap and tensor memory; release temporary bitmaps, tensors, buffers, and streams promptly.
- Respect the existing foreground-service pause/cancel/resume behaviour and checkpoint processed items.
- Do not mark a photo as successfully indexed until all per-photo processing and persistence steps have completed.
- A failed photo must be retryable. Cancellation must not be counted as an ordinary inference failure.

### 2.4 Licensing and provenance

Before adding code or a model file from any upstream source:

1. Record the exact source repository, commit/tag or model release, download URL, license, and SHA-256 checksum where available.
2. Check the license for **each** reused source file and **each** model weight. The overall repository license does not automatically settle the weights' license.
3. Record required notices in `THIRD_PARTY_NOTICES.md` or the repository's existing equivalent. If the repository has no project license file, do not invent one; report the project-license ambiguity and avoid broad source copying until resolved.
4. Do not copy the whole Ente app or pull in its account/server/sync stack. Integrate only the required face workflow components.
5. The public `laurenspriem/flutterface` repository is a demo licensed GPL-3.0, and the main `ente/ente` repository is AGPL-3.0. Treat these as meaningful license constraints, not as permission to copy code or weights with no review. Prefer a narrow, independently integrated implementation informed by documented algorithms unless copying upstream code has been explicitly assessed for compatibility.
6. If a model or source file's redistribution/use terms cannot be verified, **do not bundle or auto-download that artifact as though it were cleared**. Leave a clear, actionable blocked state and document the exact missing fact. Continue with independent architecture, interfaces, tests, and non-dependent integration work.

---

## 3. Verified upstream workflow reference

Use these as reference points, then inspect the current upstream source and model metadata before implementing exact preprocessing or parameters.

- Ente's official ML implementation overview: <https://ente.photos/ml/>
- Ente source repository: <https://github.com/ente/ente>
- Ente face-recognition help page: <https://ente.photos/help/photos/features/search-and-discovery/face-recognition>
- Ente's exploratory FlutterFace repository (GPL-3.0; do not treat this as an automatically compatible drop-in library): <https://github.com/laurenspriem/flutterface>
- YOLO5Face upstream model/project: <https://github.com/deepcam-cn/yolov5-face>
- MobileFaceNet paper: <https://arxiv.org/abs/1804.07573>
- ONNX Runtime Android documentation: <https://onnxruntime.ai/docs/get-started/with-java.html>

The public Ente implementation overview documents YOLO5Face small + NMS, similarity-transform-based alignment, MobileFaceNet embeddings, per-face metrics such as blurriness and positioning, and a custom linear incremental clustering strategy that uses face metrics and supports correction by users. It does **not**, by itself, specify every exact tensor layout, threshold, metric formula, or versioned model artifact. Verify those against the actual code/artifact chosen for this implementation.

**Important:** do not assume that every MobileFaceNet export produces the same embedding dimension. Read the actual ONNX output metadata and validate it against the configured `embeddingDimension`. Do not hard-code a guessed dimension because another MobileFaceNet model uses that size.

---

## 4. Current repository assessment (from the inspected archive)

### 4.1 Existing structure and capabilities

| Area | Current implementation | Integration consequence |
|---|---|---|
| Android stack | Kotlin, Jetpack Compose, Android SDK 35, `minSdk = 26`, Java 17 | Stay in the current stack; do not add a parallel Flutter app or rewrite UI. |
| Runtime | `com.microsoft.onnxruntime:onnxruntime-android:1.20.0` in `app/build.gradle.kts` | Can likely run the new ONNX models with the existing runtime; verify each model loads and its ops are supported. |
| Existing face pipeline | `face/BuffaloLFaceEngine.kt`; SCRFD-10GF detector + W600K_R50 ArcFace, 512-D embeddings; engine ID `buffalo_l` | Preserve this as a fully independent engine. |
| Engine abstractions | `face/FaceEngine.kt`: `FaceEngine`, `FaceEngineFactory`, `FaceEngineRegistry` | Reuse and carefully extend this abstraction rather than creating an unrelated singleton pipeline. |
| Indexing repository | `face/FaceIndexingRepository.kt` accepts a factory, but defaults to `BuffaloLFaceEngineFactory` | Caller must explicitly pass the selected factory/strategy. Current default must not determine the new engine. |
| Persistence | `face/FaceClusterStore.kt`, database `face_index.db`, currently schema version 1; rows include `engine_id`, `model_version`, embedding dimension, bounds, confidence and `quality_score` | Data is partially multi-engine-ready, but schema/ingestion/cluster APIs need exact engine scoping and Ente-specific metric/cluster support. |
| Background work | `IndexingForegroundService.kt` has a single global face task and one model-download task | Thread the selected engine ID/factory through the service intent, worker, status, cancellation, and retry paths. Keep one heavy job active at a time. |
| UI state | `IndexingStatusViewModel.kt` and `IndexingStatusUiState.kt` currently expose one face-indexing state and Buffalo_L installation state | Add per-engine state; avoid overwriting the existing state with the new engine's counters. |
| People UI | `ui/PeopleScreen.kt` reads default `FaceClusterStore` query methods | Add an explicit engine scope while preserving Buffalo_L as the default. All detail and feedback operations must use the same scope. |
| Viewer face details | `ui/ViewerDetailsDrawer.kt` reads `facesForMedia(item.uri)` and calls `FaceIndexingRepository(context)` with default factory | Ensure current default remains Buffalo_L; add selected-engine support only through an explicit engine choice. The single-photo scan must use the selected engine and its provenance. |
| Other default queries | `ui/AlbumsScreen.kt`, `ui/PeopleScreen.kt`, `ui/ViewerDetailsDrawer.kt`, `IndexingForegroundService.kt`, and `IndexingStatusViewModel.kt` use default engine IDs/counters in some paths | Audit all call sites and pass engine IDs explicitly where new-engine data is queried or changed. |
| Clustering | `FaceClusterStore.addFace()` assigns faces to nearest compatible centroid as each face is inserted. `reclusterAll(maxDistance)` is Buffalo_L-defaulted, although `reclusterAllSafely(maxDistance, engineId, modelVersion)` already takes explicit engine/provenance | A different detector/embedder alone is **not** the Ente workflow. Add an Ente-specific cluster strategy and make face ingestion separable from clustering so the current generic assignment is not mislabeled as Ente's linear incremental approach. |
| Quality data | `DetectedFaceEmbedding` has one `quality` float; `faces` persists one `quality_score` float | Extend the data contract/persistence to represent verified Ente metrics or a versioned metric payload; preserve backward compatibility for Buffalo_L. |
| DB migration | `FaceClusterStore.DATABASE_VERSION` is 1 and `onUpgrade()` currently does no migration work | Any schema changes need tested, forward-only migrations preserving all current rows and user feedback. |
| Model assets | The archive contains Tesseract data but no face-model files in `app/src/main/assets`; Buffalo_L has a dedicated download/install path | Add an independent model manager/directory and integrity checks for the new models. Do not reuse the Buffalo_L file directory or install check. |
| Existing tests | `FaceArchitectureTest.kt` covers provenance, engine-isolated indexing, cross-engine storage and basic clustering; two additional UI/physics test files exist | Extend the architecture tests; do not delete/loosen current tests to make integration pass. |

### 4.2 Concrete hard-coded call sites that must be audited

At minimum, inspect and update the relevant paths in these files:

- `app/src/main/java/com/amresalehin/emregallery/face/FaceEngine.kt`
- `app/src/main/java/com/amresalehin/emregallery/face/FaceIndexingRepository.kt`
- `app/src/main/java/com/amresalehin/emregallery/face/BuffaloLFaceEngine.kt` (preserve; add no Ente behaviour here)
- `app/src/main/java/com/amresalehin/emregallery/face/FaceClusterStore.kt`
- `app/src/main/java/com/amresalehin/emregallery/face/FaceRecognitionSettings.kt`
- `app/src/main/java/com/amresalehin/emregallery/IndexingForegroundService.kt`
- `app/src/main/java/com/amresalehin/emregallery/GalleryApplication.kt`
- `app/src/main/java/com/amresalehin/emregallery/ui/IndexingStatusViewModel.kt`
- `app/src/main/java/com/amresalehin/emregallery/ui/IndexingStatusUiState.kt`
- `app/src/main/java/com/amresalehin/emregallery/ui/FaceRecognitionCard.kt` and the screen hosting it
- `app/src/main/java/com/amresalehin/emregallery/ui/PeopleScreen.kt`
- `app/src/main/java/com/amresalehin/emregallery/ui/ViewerDetailsDrawer.kt`
- `app/src/main/java/com/amresalehin/emregallery/ui/AlbumsScreen.kt`
- `app/src/test/java/com/amresalehin/emregallery/FaceArchitectureTest.kt`

Use `rg`/IDE references to find all defaulted face-store/repository calls; the list above is a starting point, not an exhaustive list.

### 4.3 Baseline build/test limitation

A baseline attempt to run `bash gradlew testDebugUnitTest --no-daemon` could not start because the Gradle wrapper attempted to download Gradle 9.3.1 and the environment could not resolve `services.gradle.org` (`UnknownHostException`). This is an environment/network blocker, **not evidence that the application source currently compiles or fails to compile**. Retry the tests in an environment with the required Gradle distribution/dependencies available and report the distinction clearly.

---

## 5. Target architecture

### 5.1 Stable engine identity and provenance

Add one stable engine identifier, for example:

```kotlin
const val ENGINE_ID = "ente_yolo5face_mobilefacenet"
```

The final string must be chosen once, documented, and never changed casually after data has been stored. Define constants in one canonical class/object; do not scatter magic strings.

The engine must declare:

- stable `engineId`;
- display name that distinguishes it from Buffalo_L;
- exact detector + embedder model version/revision;
- validated embedding dimension read/checked against model metadata;
- format version for persisted embeddings/metadata;
- a unique model-install directory;
- its own settings namespace;
- its own clustering strategy identifier/version;
- whether the model pack is present and valid;
- explicit download/install/revalidation methods.

Do not define Ente's identity as `buffalo_l`, and do not use the same `modelVersion` as Buffalo_L.

### 5.2 Engine registry

Use `FaceEngineRegistry` as the central registry (or replace it only with a narrowly equivalent, tested implementation).

- Register both factories deterministically at application startup.
- Avoid relying solely on incidental class-loading/static `init` side effects to register an engine.
- `listEngines()` must reliably return both engines in a stable order.
- `getFactory(engineId)` must return the exact factory requested or a clear unknown-engine result.
- Remove any assumption that `getActiveFactory()` controls every repository/UI path. Any “selected engine” must be passed explicitly or stored as an explicit user setting.
- Preserve the existing default active/primary engine as Buffalo_L for backward compatibility.
- Do not make registration silently choose the new engine as a replacement for the current one.

### 5.3 Separate inference from clustering

The `FaceEngine` contract can remain the inference boundary, but the workflow must support different clustering algorithms.

- Add a small, explicit clustering-strategy abstraction (or an equivalent engine-owned workflow strategy) that is selected by each factory.
- Buffalo_L must continue using its current expected behaviour unless regression tests prove an existing issue. Do not opportunistically rewrite the existing cluster algorithm in this task.
- The Ente-style engine must have its own incremental clustering implementation, its own versioned thresholds/metric policy, and engine-scoped user feedback.
- Refactor persistence so a face can be recorded before its final cluster assignment, or use another safe design that lets the Ente workflow run its own algorithm. Do not route Ente faces through `FaceClusterStore.addFace()`'s existing generic nearest-centroid assignment and then claim the Ente cluster workflow has been implemented.
- Keep shared storage primitives generic; keep engine-specific clustering policy outside the generic SQLite persistence layer where practical.
- All clusterer APIs must receive `engineId` and `modelVersion` explicitly. No implicit Buffalo_L defaults are allowed inside Ente-specific paths.

### 5.4 Proposed class/file layout

Use this as a guide, adjusting names only when the repository's existing patterns suggest a better fit. Avoid one enormous class that owns model downloading, tensor decoding, alignment, clustering, persistence, and UI orchestration.

```text
app/src/main/java/com/amresalehin/emregallery/face/
  FaceEngine.kt                         # shared contract / registry updates
  FaceIndexingRepository.kt             # selected engine + strategy injection
  FaceQualityMetrics.kt                 # new typed metric model if needed
  EnteFaceEngine.kt                     # detector + aligner + embedder orchestration
  EnteFaceEngineFactory.kt              # registry-facing factory
  EnteFaceModelManager.kt               # verify/download/install model artifacts
  EnteFaceDetector.kt                   # YOLO5Face tensor decode + NMS
  EnteFaceAligner.kt                    # landmark-based similarity transform
  EnteFaceEmbedder.kt                   # MobileFaceNet ONNX inference
  EnteFaceClusteringStrategy.kt          # chronological incremental cluster strategy
  FaceEngineModels.kt                   # stable IDs/model metadata if useful
```

Do not create empty placeholder classes merely to match this tree. Each class should have one concrete responsibility and tests.

---

## 6. Phase 0 — Inspect upstream artifacts and settle implementation facts

**Deliverable:** an implementation note (can be added to the final task report or `docs/architecture/face-engines.md`) containing exact artifacts and the facts confirmed below.

### Tasks

- [ ] Check the current official Ente repository revision and locate the currently maintained face model/pipeline code. Use the current source tree, not search-result snippets alone.
- [ ] Determine which exact YOLO5Face small ONNX artifact is intended for mobile production use. Record its source, revision, hash, input name/shape/layout, output name(s), coordinate conventions, landmark order, and output decoding/anchors/strides if applicable.
- [ ] Determine which exact MobileFaceNet ONNX artifact is intended. Record source, revision, hash, input name/shape/layout, channel order, normalization, output name/shape, embedding dimension, and normalization contract.
- [ ] Confirm the original preprocessing/resizing/letterbox policy, NMS IoU threshold, confidence threshold policy, maximum detections, and bounding-box coordinate mapping from the verified source. Do not inherit Buffalo_L's constants by default.
- [ ] Confirm the exact landmark order and the reference points, transform solver, crop dimensions, interpolation method, and pixel normalization used before embedding inference.
- [ ] Locate the actual face quality metrics used by the current upstream implementation. Determine whether each metric can be implemented on Android from available outputs/pixels. Document anything not exposed by public source as “not verified”; do not fabricate metric names/formulas or claim exact parity.
- [ ] Verify the current clustering algorithm's sort/chronology rule, centroid/representative selection, treatment of quality, ambiguous match behaviour, new-photo insertion semantics, and preservation of manual feedback. Prefer a direct code-level reading over reverse engineering from the overview article.
- [ ] Verify all license obligations for the source and both model weights. Record notices and hashes before using the artifacts.
- [ ] Confirm the current ONNX Runtime Android version supports both models and required operators on the project's target ABIs (`arm64-v8a`, `x86_64`). If a model cannot load, fail with a diagnostic rather than silently changing engine.
- [ ] Write a short fact sheet in the code/docs: `verified`, `approximation`, or `blocked` for each critical item.

### Exit criteria

No guessed tensor dimensions/preprocessing constants remain in the implementation plan. License and model provenance have a documented status. If an artifact is not cleared or cannot be identified reliably, do not ship it; explain the precise blocker and proceed with the independent engine framework and tests.

---

## 7. Phase 1 — Harden the multi-engine contracts without changing Buffalo_L behaviour

### Tasks

- [ ] Audit `FaceEngine`, `FaceEngineFactory`, `FaceEngineRegistry`, and `FaceIndexingRepository` for assumptions that there is only one engine.
- [ ] Make engine registration deterministic at app startup; register Buffalo_L and the new factory explicitly.
- [ ] Add a typed engine lookup/result. Unknown IDs must not fall back to Buffalo_L.
- [ ] Ensure `FaceIndexingRepository` receives an explicit factory for every new engine operation. Keep its existing Buffalo_L default only where backwards compatibility requires it; new code must never rely on that default.
- [ ] Define an explicit clustering strategy contract or equivalent workflow hook. Document which strategy belongs to Buffalo_L and which belongs to the Ente-style engine.
- [ ] Define common progress/error result types that include the engine ID. UI and logs must never confuse which engine generated a counter or failure.
- [ ] Add tests for registry contents, stable engine IDs, missing-factory behaviour, and explicit factory injection.
- [ ] Verify `embeddingDimension` is checked against the ONNX model output shape at session initialization. Do not permit a model/version change without a new or deliberately migrated provenance value.

### Exit criteria

Both factories can be found deterministically by ID. A request for an invalid ID fails with an explicit error. Current Buffalo_L tests pass without changing the engine ID, model version, or default behaviour.

---

## 8. Phase 2 — Implement isolated model acquisition and validation

### Tasks

- [ ] Implement `EnteFaceModelManager` (or equivalent) for the new engine's complete model pack.
- [ ] Store model files under an Ente-specific private directory such as `filesDir/face-models/ente_yolo5face_mobilefacenet/`; never reuse `filesDir/face-models/buffalo_l/`.
- [ ] Keep detector and embedder artifact URLs, hashes, expected sizes, filenames, model versions, and license metadata in one documented manifest/config object.
- [ ] Use pinned/versioned artifact URLs rather than mutable branch URLs when a release/commit-pinned source is available.
- [ ] Verify each downloaded artifact using SHA-256 (or a stronger upstream-provided signature where available). Model validation must check file presence, non-trivial size, hash, and loadability—not just the file name.
- [ ] Use temporary `.part` files and a staging directory; install only after all files pass checks. Use atomic rename or safe replacement. Do not leave a partially installed model pack in a state reported as “ready”.
- [ ] Prevent archive path traversal (Zip Slip) if archive extraction is necessary. Reject unexpected paths and files; extract only the explicitly expected artifacts/notices.
- [ ] Honour cancel/pause/resume hooks and expose byte/stage progress to the existing service/UI.
- [ ] Check free storage space before download and extraction. Include temporary staging space in the safety check; return a useful storage error.
- [ ] Make re-download/repair independent of Buffalo_L. Force-download of the new model must never delete or replace Buffalo_L files.
- [ ] Provide a model-state result that distinguishes `not downloaded`, `downloading`, `installed and valid`, `corrupt/incomplete`, `unsupported/runtime failure`, and `license/source blocked` where relevant.
- [ ] Store the applicable license and notice text with the model pack or in the app's third-party notice documentation as required.
- [ ] Add tests for valid install, wrong hash, missing file, undersized file, truncated download, cancellation before commit, safe retry, and no damage to the Buffalo_L directory.

### Exit criteria

The new model pack validates independently. A failed download cannot be mistaken for an installed model. No model artifact is shipped/downloaded until its provenance and license decision has been recorded.

---

## 9. Phase 3 — Implement the Ente-style inference pipeline

Implement inference in testable stages. Use actual artifact metadata and verified upstream source for all tensor decoding.

### 9.1 Decode and prepare the source image

- [ ] Open the `MediaItem` URI with the existing Android `ContentResolver` path; do not assume the URI maps to a normal filesystem path.
- [ ] Respect EXIF orientation before detector inference and coordinate calculation. Ensure faces drawn later in the viewer align with the displayed image after rotation/cropping.
- [ ] Decode at an appropriate resolution for the verified detection pipeline. Do not load an unbounded batch of full-resolution bitmaps.
- [ ] Handle revoked/missing URI permission, corrupt image, unsupported format, and memory pressure as per-item errors; do not crash the entire scan.
- [ ] Avoid retaining image bitmaps after indexing completes.

### 9.2 YOLO5Face detection

- [ ] Implement detector preprocessing exactly as verified for the selected ONNX export: input dimensions, RGB/BGR, range/mean/std, resize strategy, letterbox, padding, and tensor layout.
- [ ] Validate model input/output names and shapes at startup. Fail with an actionable error if the artifact differs from the manifest.
- [ ] Decode detection outputs using the model's verified output contract, including the five landmark points if present. Do not use the Buffalo_L SCRFD decoder or constants.
- [ ] Apply the configured confidence filtering and NMS consistent with the verified upstream behavior. Make thresholds explicit constants/settings under the new engine namespace.
- [ ] Map boxes and landmarks from model input coordinates back to the orientation-correct source bitmap coordinates with correct handling of padding/scale.
- [ ] Clamp bounds to the image rectangle, reject invalid/degenerate boxes, and filter impossible coordinates/non-finite scores.
- [ ] Make ordering stable for deterministic tests: define ordering by confidence, then geometry or another documented tie-breaker.
- [ ] Test one face, multiple faces, overlapping detections, no faces, tiny face, edge-of-image face, rotated image, invalid output, and NMS tie cases.

### 9.3 Similarity-transform face alignment

- [ ] Implement landmark-based similarity alignment from verified upstream logic, in a dedicated class/helper.
- [ ] Encode the model's precise landmark order and reference template explicitly. Add comments explaining the order and source.
- [ ] Confirm transform direction and coordinate spaces with a synthetic landmark test; do not rely on visual guesswork alone.
- [ ] Use the verified output crop size/interpolation/filter and channel normalization required by the chosen embedder.
- [ ] Guard against missing/degenerate landmarks, singular transforms, faces too small for useful alignment, and crop bounds outside the image. Define a deterministic rejection/fallback rule based on verified upstream behaviour.
- [ ] Do not fall back to a center crop while marking the face as if it had been aligned correctly.
- [ ] Test that a known synthetic landmark arrangement maps to the expected aligned output geometry within tolerance.

### 9.4 MobileFaceNet embedding

- [ ] Load the verified MobileFaceNet model from the new engine's model directory using the existing ONNX Runtime dependency.
- [ ] Configure safe, measured session options for Android. Do not create excessive inference threads by default.
- [ ] Assert input and output tensors match the model manifest; check output shape/dimension during engine construction.
- [ ] Apply exact verified color order, input shape, pixel scaling/normalization, and embedding post-processing. Do not guess normalization from another export.
- [ ] Convert output to a finite float array, validate length, normalize as required by the model, reject NaN/Inf/zero-norm output, and persist the output dimension/version as provenance.
- [ ] Ensure embeddings are never fed into Buffalo_L's centroid/threshold logic.
- [ ] Reuse both ONNX sessions across a photo batch and close both sessions in every exit path.
- [ ] Add deterministic tests for input preprocessing values, output-shape validation, normalization, invalid output, session lifecycle, and model initialization errors. Use a test double for inference where ONNX binaries are unavailable in unit-test environments.

### 9.5 Face quality metrics

- [ ] Introduce a typed `FaceQualityMetrics` object or equivalent that separates detector confidence from image quality.
- [ ] Implement the upstream-supported blur and position/pose-related signals only after verifying the actual computation. Write down the metric meaning, range, directionality (e.g., higher-is-better), and version.
- [ ] Keep detector confidence and face quality separate. Do not collapse detector score into blur/pose quality.
- [ ] Ensure quality metrics are finite and normalized consistently. Define how a missing metric is represented so it cannot be mistaken for a perfect score.
- [ ] If exact upstream metric formulas are unavailable, implement only a transparently documented approximation and do not claim exact Ente parity; keep the data contract versioned so it can be improved later.
- [ ] Add tests with sharp/blurred, frontal/off-angle, small/large, and invalid face-crop examples where meaningful.

### Exit criteria

The new engine can process a small fixture set on a real Android device/emulator with valid model files and return correctly mapped boxes, valid aligned-face embeddings, and quality metadata. It does not modify the existing Buffalo_L implementation.

---

## 10. Phase 4 — Add a genuinely Ente-style incremental clustering path

This phase is mandatory. Do not consider the task complete if the new detector/embedder feeds the existing centroid path only.

### 10.1 Separate raw face indexing from final clustering

- [ ] Inspect the current `FaceClusterStore.addFace()` and schema constraints. It currently creates/assigns a cluster as part of face insertion.
- [ ] Add a safe persistence path allowing the new engine's per-face detection/embedding/metrics to be committed before or independently of final grouping. It may use a new method plus transactionally maintained placeholder clusters, or another clearly tested design. Do not break Buffalo_L's existing insertion contract.
- [ ] Ensure the database can represent an indexed photo with zero faces; “no faces found” must be a completed index state, not a repeated indexing loop.
- [ ] Ensure face insertion is idempotent for `(media_uri, face_index, engine_id, model_version)` and cannot create duplicate embeddings during a retry.
- [ ] For a photo-level failure, roll back partial writes for that photo or explicitly make the resume path reconcile/delete only that engine's incomplete work. Never leave orphaned face rows silently labelled indexed.

### 10.2 Implement the incremental clustering strategy

- [ ] Add an `EnteFaceClusteringStrategy` (or equivalent) selected only for the new engine.
- [ ] Use the chronological order rule verified from current Ente source. Define a deterministic ordering for photos and faces; do not depend on arbitrary SQLite row order.
- [ ] Implement the documented linear incremental assignment behaviour. New data should be incorporated without needlessly dismantling stable, user-corrected clusters; repeated clustering on unchanged data should be idempotent within the defined behaviour.
- [ ] Incorporate verified face quality metrics into grouping decisions. Avoid placing low-quality/ambiguous faces into a person solely because their embedding is barely within a global threshold.
- [ ] Separate a **cluster of similar face observations** from a **person entity** if needed to model the upstream's distinction. A person may contain multiple face clusters; do not force this into a one-person/one-cluster assumption if it would prevent the required behaviour.
- [ ] Use the exact embedding model version and dimension as a compatibility boundary. No cluster may include embeddings from another engine or incompatible model version.
- [ ] Record strategy/version provenance so future algorithm changes can be migrated/reclustered intentionally.
- [ ] Handle ambiguous matches with a review suggestion or an explicit unassigned/low-confidence state rather than forcing a confident identity.
- [ ] Reuse existing correction records only with explicit engine scoping. Feedback for the new engine must not affect Buffalo_L clustering and vice versa.
- [ ] Preserve user-named identities/ignored persons and face-level remove/assignment feedback during reprocessing. Define deterministic conflict rules and test them.
- [ ] Add tests for chronological ordering, similar-person cluster assignment, different-person separation, low-quality ambiguity, multi-cluster person behaviour if implemented, incremental additions, repeated run/idempotence, correction preservation, and engine/model-version isolation.

### 10.3 Store schema and migration

- [ ] If additional face metrics or strategy metadata require schema changes, increment `FaceClusterStore.DATABASE_VERSION` and implement explicit forward migrations in `onUpgrade()`. Do not leave `onUpgrade()` as a no-op after adding columns/tables.
- [ ] Prefer an engine-scoped metrics table keyed by `(media_uri, face_index, engine_id)` or an equivalent structure rather than storing opaque unversioned data in `quality_score` alone.
- [ ] Persist metric schema/version and algorithm/model provenance where needed.
- [ ] Preserve every existing v1 table/row, especially Buffalo_L face rows, names, covers, hidden state, suggestions, feedback, and indexed-media checkpoints.
- [ ] Test migration using a real v1 test database seeded with representative Buffalo_L rows and verify those rows are byte-/value-preserved after upgrade.
- [ ] Make every new query filter on engine and compatible model version. Database defaults are not a substitute for explicit scoping in new code.
- [ ] Update all data classes with backward-compatible defaults where needed; do not break existing callers unnecessarily.

### Exit criteria

The new engine uses its own tested clustering strategy with quality-aware incremental updates. Existing Buffalo_L data and workflow remain isolated and unchanged.

---

## 11. Phase 5 — Wire the new engine into foreground work, cancellation, progress, and retry

### Tasks

- [ ] Add an explicit `engineId` extra to face-indexing service commands. Validate the ID before starting work.
- [ ] Route `runFace(engineId, rescanAll)` to the selected `FaceEngineFactory`, correct clustering strategy, model manager, and engine-specific counters.
- [ ] Route model download/repair through the selected factory. Do not assume every engine's model pack or stages use Buffalo_L's filenames or messages.
- [ ] Ensure rescan resets only the chosen engine's indexed-media checkpoint and only that engine's derived cluster state if the specific action explicitly requires it. Preserve named identities/feedback according to the documented rescan semantics.
- [ ] Replace every Buffalo_L-only counter inside generic service paths with an explicit selected engine ID: indexed photos, indexed faces, failure count, clusters, and per-item progress.
- [ ] Include engine display name or ID in progress stage/status so the UI can identify which engine is running.
- [ ] Keep one global heavy face task at a time in the current foreground service. Reject/disable a second face-index task while one is active, with clear UI state, instead of allowing two models to compete for memory.
- [ ] Pause/resume at safe per-photo/per-download boundaries. Check cancellation before decoding, after inference, before persistence, and before advancing to the next item.
- [ ] Rethrow `CancellationException`; never mark a deliberately cancelled item as a permanent failure.
- [ ] Ensure failures are counted and displayed by engine. Do not swallow exceptions with no diagnostic. Avoid logging image content, raw embeddings, landmarks, or personally identifying face information.
- [ ] Ensure the single-photo scan from the viewer injects the selected engine explicitly and respects current indexing/model status. It must not accidentally create Buffalo_L faces if the user requested Ente-style results.
- [ ] Add tests for engine-specific `rescanAll`, pause/resume, cancel, download error, retry of failed media, zero-face success, and service status showing the correct engine.

### Exit criteria

Both workflows are selectable and independently resumable/retryable using the same background-work framework. There is no silent engine fallback and no accidental data reset across engines.

---

## 12. Phase 6 — UI integration (minimal, explicit, no unrelated redesign)

### 12.1 Indexing status UI

- [ ] Preserve the current Buffalo_L card and its existing behaviour.
- [ ] Add a separate face-engine card/section for the new workflow using the app's existing monochrome visual language and component patterns.
- [ ] Show the new engine's display name plus model readiness, indexed photo count, detected face count, failure count, progress, current stage, and cluster/person count where available.
- [ ] Provide separate actions for the new engine: download/repair model, scan/index, pause/resume, cancel, rescan, and cluster/recluster as appropriate to the actual strategy. Disable actions when unavailable/unsafe.
- [ ] Show an explicit error state when model provenance/license is blocked, a download fails, a model hash is wrong, ONNX loading fails, or a photo fails. Do not report “ready” for invalid models.
- [ ] Keep model status and indexing state per engine; a Buffalo_L download must not mark Ente ready and vice versa.
- [ ] Add UI-state models keyed by engine ID or separate, typed engine states. Do not repurpose the Buffalo_L state fields for both at once.
- [ ] Names and actions must not imply that the new engine is the current default/primary recognition model. Use a neutral label such as `YOLO5Face + MobileFaceNet` or `Ente-style workflow` and avoid implying official affiliation.

### 12.2 People screen

- [ ] Add a small engine selector/scope control for People results. The initial/default selection must remain `buffalo_l` so current users see the same people after upgrade.
- [ ] All queries inside `PeopleScreen` must use its selected `engineId`: visible people, hidden/ignored people, pending suggestions, faces in a person, detail photo list, and reload.
- [ ] All actions must pass the same `engineId`: rename, merge, split, ignore/hide, cover selection, face removal, accept/reject suggestion, and reset/recluster.
- [ ] Do not auto-copy names or person IDs between engine namespaces. If later offering a user-driven name copy, it must be explicit and must not copy embeddings or cluster membership.
- [ ] Keep existing people UI layout/behaviour. Do not change unrelated tabs or insert fake “Unknown” labels. Empty names remain empty/unnamed as the existing UI expects.
- [ ] Ensure person counts and cover photos are selected from the correct engine's rows only.

### 12.3 Viewer detail drawer and Albums

- [ ] The viewer's detected-faces overlay and face edit actions must read the currently selected engine scope, defaulting to Buffalo_L.
- [ ] A scan launched from the viewer must use the same selected engine and update the same scope on completion.
- [ ] If engine switching inside the viewer would add too much UI complexity, keep the viewer on the app's explicit default engine and provide the full engine selector in People/Indexing status; do not silently mix engines.
- [ ] `AlbumsScreen` face/person query behaviour must remain Buffalo_L by default. If the new engine is exposed there, add an explicit engine filter and apply it to all the relevant queries.
- [ ] Verify `facesForCluster()` is always called with the expected `engineId` when data could come from the new engine; do not rely on unscoped methods that query both engines.

### Exit criteria

Users can see separate engine progress and choose which engine's people they are reviewing. Existing screens continue to show Buffalo_L data by default. The new engine is visible without merging identities across engines.

---

## 13. Phase 7 — Add side-by-side engine evaluation

The repository already has a rudimentary `FaceClusterStore.compareEnginesOnMedia()` path that compares detections using bounding-box overlap. Verify its semantics and extend only as needed.

### Tasks

- [ ] Provide a way to run both engines on the same selected/small sample set sequentially, not simultaneously by default.
- [ ] Report per engine: photos successfully indexed, photos failed, face count, average processing time per photo, total processing time, and model/runtime errors.
- [ ] For a shared photo, compare detector overlap using an explicit IoU definition and threshold. Report unmatched detections separately; do not conflate same bounding-box overlap with same-person recognition.
- [ ] Do not compare cosine distances between Buffalo_L and MobileFaceNet embeddings. The embeddings are produced by different models and are not assumed to share an embedding space.
- [ ] Add a comparison view/result summary that is optional and does not hijack the normal People screen.
- [ ] Keep test/evaluation sets local and do not transmit photos or face data.
- [ ] Record engine IDs, model versions, thresholds, device information appropriate for reproducibility, and timestamp in an exportable local report if a report is implemented. Do not include raw embeddings.
- [ ] Avoid presenting a tiny sample's detection count as proof that one engine is universally “more accurate”. Label it as a local comparison, not a benchmark conclusion.

### Exit criteria

A test sample can be indexed independently by both engines and the app can present valid detection-level comparison metrics while keeping each engine's people identities isolated.

---

## 14. Phase 8 — Tests and validation matrix

Keep `FaceArchitectureTest.kt` and add focused tests instead of relying on a full-library manual scan as the first validation.

### 14.1 Unit / Robolectric tests

- [ ] Registry exposes both engines and rejects an unknown engine ID.
- [ ] Buffalo_L defaults and constants are unchanged.
- [ ] Same URI can be marked complete independently for each engine.
- [ ] Same `(media_uri, face_index)` can exist once per engine without primary-key collision.
- [ ] Querying People for one engine never returns another engine's person/face/suggestion.
- [ ] Renaming/merging/hiding/removing a face in one engine cannot modify the other engine's records.
- [ ] Embedding dimensions and model versions are validated; incompatible embeddings cannot be clustered.
- [ ] New model manager rejects incomplete or checksum-mismatched weights and leaves the other model folder untouched.
- [ ] Detector decoder maps test outputs to expected boxes/landmarks; NMS is stable.
- [ ] Face alignment transform is correct for a synthetic landmark fixture.
- [ ] MobileFaceNet preprocessing/output normalization agrees with the verified artifact contract.
- [ ] Quality metrics have documented range and invalid/missing-value behaviour.
- [ ] Clustering is deterministic for the same ordered input, incremental, engine-isolated, and correction-preserving.
- [ ] Database v1-to-v2 migration preserves all seeded Buffalo_L information.
- [ ] Photo-level failures roll back/invalidate partial data and can be retried.
- [ ] Cancellation and pause/resume do not leave false “done” checkpoints.

### 14.2 Instrumented/device validation

Run on at least one physical Android device with `arm64-v8a`, and an emulator/CI configuration where practical.

- [ ] New model pack downloads or installs only from a verified source and then passes integrity checks.
- [ ] At least one image with no visible face returns zero detections and completes successfully.
- [ ] A multi-face image returns unique, correctly bounded detections after NMS.
- [ ] Portrait orientation/EXIF rotation produces correctly aligned boxes in the viewer overlay.
- [ ] Faces with a small head/face size do not crash the alignment/embedder stages.
- [ ] Large image processing does not cause an avoidable out-of-memory failure. Observe memory and processing time on device; if the issue exists, reduce decode size/thread count rather than batch more bitmaps.
- [ ] Pause and resume during a long scan works; process death/restart does not corrupt checkpoints.
- [ ] Force-rescan of Ente-style engine leaves Buffalo_L results and names unchanged.
- [ ] Force-rescan of Buffalo_L leaves Ente-style results unchanged.
- [ ] Switching People engine scope changes the listed faces/counters appropriately, without stale selected person state carrying into the other engine.
- [ ] No raw photo, face crop, embedding, landmark coordinates, or identity data is sent over the network.

### 14.3 Build/CI checks

- [ ] Run the repository's wrapper build/test commands in an environment where the Gradle distribution/dependencies can be fetched or are pre-cached.
- [ ] Run `testDebugUnitTest`, then `assembleDebug` (or the repository's actual equivalent task names).
- [ ] Inspect release minification/R8 rules if any ONNX model/session reflection needs keep rules; add only evidence-based rules.
- [ ] Confirm the existing `.github/workflows/android-build.yml` still builds the project.
- [ ] Report any test that could not run due to DNS, dependency resolution, missing artifact, or lack of device separately from a genuine source/test failure.

### 14.4 No-regression checks

- [ ] Existing `FaceArchitectureTest` cases pass without weakening assertions.
- [ ] Existing OCR, location indexing, photo map, viewer, albums, and permissions still compile and behave as before.
- [ ] The `applicationId`, `namespace`, minimum SDK, current ABI filters, and current gallery navigation remain unchanged unless a verified technical need requires a separate explicit justification.

---

## 15. Operational and privacy requirements

- [ ] Perform inference locally and offline after model installation. Do not add a cloud endpoint for recognition.
- [ ] Never log image paths with personal filenames unnecessarily, face crops, full landmark arrays, raw embeddings, or person names. Keep logs to engine ID, photo processing state, stage, counts, and sanitized exception details.
- [ ] Keep all model files and the SQLite face database in app-private storage. Do not add external-storage write logic for embeddings/models.
- [ ] Keep model download URLs under HTTPS and use integrity checks. Handle redirect limits safely.
- [ ] Ensure any new database content remains governed by the app's existing backup policy (`android:allowBackup="false"` in the inspected manifest); do not relax that privacy setting.
- [ ] Keep UI accessible: readable progress/status, actionable error copy, and disabled actions where the model is unavailable or the service is busy.
- [ ] Add basic safeguards for the new workflow: bounded threads, no default concurrent face sessions, low-storage failure, and recoverable interrupted processing. Battery/thermal-aware scheduling may be added if it fits current architecture, but do not implement a broad background-job rewrite in this task.

---

## 16. Explicitly out of scope

Do not do any of the following in this task:

- [ ] Replace Buffalo_L with the Ente-style engine or change the default engine to the new pipeline.
- [ ] Delete Buffalo_L code, models, saved embeddings, or named people.
- [ ] Port Ente's account/session/auth/cloud-sync/server code.
- [ ] Upload photos or embeddings to Ente, Google, Gemini, or any other cloud service.
- [ ] Add semantic search, image captioning, OCR changes, object recognition, pets recognition, or general AI tagging.
- [ ] Redesign the Gallery home screen, viewer drawer physics, navigation system, map/heatmap, album system, or theme.
- [ ] Change Android permission requests unrelated to face indexing.
- [ ] Introduce an automatic face/name merge across engines.
- [ ] Call the implementation “exact Ente parity” unless the actual upstream model versions, preprocessing, metrics, clustering, feedback behaviour, and relevant validation have been verified and reproduced.
- [ ] Silence failures, make test assertions weaker, or replace robust tests with a superficial “model loaded” test.

---

## 17. Suggested implementation order (do not skip ahead)

1. **Audit + upstream fact sheet** — source paths, exact model versions, output dimensions, license, hashes, preprocessing facts.
2. **Multi-engine contracts** — registry, explicit IDs, factory/strategy boundaries, regression tests.
3. **Model manager** — verified download/install, status, integrity, safe cancellation/retry.
4. **Inference components** — detector, NMS, alignment, embedder, typed quality metrics; test each component.
5. **Persistence changes** — raw face ingestion, metrics storage, schema migrations, engine-scoped query/mutation APIs.
6. **Ente-style clustering** — chronological incremental strategy + quality/feedback handling; test determinism/idempotence/isolation.
7. **Service/orchestration** — selected-engine IDs through start, pause, cancel, rescan, progress, retries.
8. **UI** — separate engine card/state and explicit People engine scope; Buffalo_L remains default.
9. **Comparison mode** — sequential sample indexing and detector-overlap/performance summary.
10. **Full validation** — unit tests, migration tests, build, physical-device smoke test, licence/notices review.
11. **Documentation/final report** — architecture, model provenance, exact implemented-vs-approximate upstream behaviours, tests and blockers.

Do not start the large UI change before the engine ID/data isolation tests pass.

---

## 18. Final acceptance checklist

The task is complete only when all applicable items below are true:

- [ ] A new stable Ente-style engine ID and factory are registered deterministically alongside `buffalo_l`.
- [ ] The new engine implements a verified YOLO5Face detector, NMS, verified landmark alignment, and verified MobileFaceNet inference with dimension/model-version validation.
- [ ] The new engine records face quality metrics as explicit, versioned data; unknown formulas are not misrepresented as exact upstream behaviour.
- [ ] The new engine has a distinct linear incremental clustering strategy, rather than the current generic Buffalo_L default clustering path relabelled as Ente.
- [ ] The database migration, if needed, preserves all existing data and is tested using a v1 fixture.
- [ ] All indexing, rescan, reset, query, merge, split, ignore/hide, and face-removal operations are engine-scoped.
- [ ] Current Buffalo_L remains the default and continues to work with existing stored data.
- [ ] UI shows independent model status, indexing counts/progress/errors, and a way to review the new engine's People results.
- [ ] Both engines can index the same URI without cross-engine skips, primary-key collisions, mixed clusters, or identity copying.
- [ ] Only one heavy face task runs at a time by default; pause/cancel/resume/retry are safe.
- [ ] Engine comparison is detection-level unless/ until a scientifically valid cross-engine evaluation is designed; embeddings from unlike models are not compared directly.
- [ ] Model sources, version pins, checksums, and licenses are documented; required third-party notices are included.
- [ ] Existing unit tests pass; new isolation, migration, model-validation, detector/alignment, clustering, and orchestration tests are added.
- [ ] Build/test command outcomes are reported accurately, including environmental blockers.
- [ ] No unrelated gallery feature was redesigned or regressed.

### Required final response from the coding agent

At completion, report:

1. **Implemented:** concise list of concrete capabilities.
2. **Changed files:** grouped by engine/inference, database/clustering, service/UI, tests/docs.
3. **Engine details:** engine ID, exact model versions, embedding dimension, input preprocessing, metric fields, cluster strategy/version.
4. **License/provenance:** source URLs, artifact hashes, license status, notice file path, and any unresolved model-rights issue.
5. **Validation:** exact build/test/device commands and pass/fail/blocker for each.
6. **Regression evidence:** how the agent verified Buffalo_L's IDs/data/UI remain the same and Ente data is isolated.
7. **Known gaps:** only specific, reproducible limitations—never generic “needs more testing”.

Do not claim completion if the implementation only swaps models, only adds a factory, or only adds a UI card without end-to-end independent indexing and clustering.
