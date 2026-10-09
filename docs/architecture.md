# Architecture

Obadh Android has three modules: `:app` owns product settings and the Bangla
adapter; `:keyboard` owns the native editor, layouts, gestures and English runtime;
`:engine` owns the shared Kotlin/JNI bridge to Obadh Engine's C ABI. The engine
owns transliteration, FST ranking, n-gram prediction and model parsing. Android
owns UI, asset installation, correction acceptance policy and editor transactions.

The keyboard service runs in `:keyboard`; the Material application runs separately.
One canonical configuration is delivered explicitly to the service. There is no
second Bangla keyboard implementation registered, and no independent settings
catalog for English. Data deletion commands run in the owning process and return
an acknowledgement to the app.

The shared bridge passes UTF-8 buffers and packed records across JNI, checks ABI
compatibility and locks each opaque handle separately. Fast deterministic
conversion stays synchronous. Models, native suggestions, bilingual emoji search
and coalesced personal snapshot writes run on workers. Composer generations reject
stale suggestion results. Boundary commits use the current correction policy.

Bangla learning uses the engine's fingerprint-validated personal overlay and
protected-spelling store. English uses the native dictionary. Native clipboard
history uses a private database; root preferences and emoji variant selections use
small key-value stores. Optional swipe download is handled by Android
DownloadManager plus verification WorkManager; no typing data is uploaded.

See [the integration guide](heliboard-integration.md) for the seam map, settings
ownership, field behavior, performance constraints, source preparation and update
workflow. [Autocorrect](autocorrect.md) documents the policy reused by the adapter.
Older layout/composition documents retain legacy implementation details as marked
reference; the generated native host and owned extension are the current runtime.
