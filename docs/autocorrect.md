# Autocorrect, auto-insert, and learning

The [engine](https://github.com/nsssayom/obadh_engine) supplies *signals*; this
client owns *policy*. That split is deliberate and shared with iOS: a
data-dependent policy the engine's CI cannot test (it runs without the data
artifacts) should not live in the engine. The engine ships provenance
(`suggest_detailed`: per-candidate channel, costs, frequency, plus
`word_frequency` as the baseline signal) and states plainly that whether to
silently apply a correction "is a client decision." The full rationale is in
[obadh-ios/docs/autocorrect.md](../../obadh-ios/docs/autocorrect.md); this
document records what the Android port does and where it differs.

## The suggestion flow

The inline composition remains the deterministic transliteration. One background
`detailedCorrections` query on the worker executor supplies both ribbon
candidates and their provenance; stale generations are discarded when the result
returns to the main thread.

**Exact English loanwords are the exception to literal-first behavior.** The
engine matches them case-insensitively (source 9, Roman repair cost 0). The
highest-ranked exact match leads the ribbon and commits on Space, Return or
punctuation, even with Auto-Insert Corrections off. The literal is the quoted,
tappable second entry. Exact matches bypass typo costs, frequency thresholds and
learned-spelling protection; explicitly tapping the literal still commits that
spelling. Fuzzy loanwords never receive this exception.

Other input retains literal-first ordering, quoting non-dictionary literals.
Keeping a spelling (tapping the quoted literal) protects it from ordinary
auto-insertion. If a delimiter arrives before the background result, the
composer resolves the token once at commit so loanword acceptance does not
depend on typing speed.

## The auto-insert gate

Ordinary typo correction is off by default (Settings › Auto-Insert Corrections).
After the exact-loanword policy above, when enabled, space commits the top
correction instead of the literal only when **every** hurdle passes
(`AutoInsertGate`):

1. **Confident channel.** The candidate must come from a typo-shaped channel:
   edit-distance, diacritic edit, orthographic vowel-length, consonant confusion,
   roman-repair-exact, or loanword-exact. Completion and fuzzy channels never
   fire; *unknown channel codes never fire* (the codes are frozen and
   append-only).
2. **Per-channel cost ceiling.** Costs are channel-specific weighted distances,
   not grapheme counts: মানুস→মানুষ reports edit cost 3 through
   consonant-confusion for a single swap. Ceilings: confusion ≤ 3, everything
   else ≤ 1, roman repair ≤ 1.
3. **Frequency floor** (40): the correction must be a common-enough real word.
4. **Baseline rule.** The typed literal must be a non-word, *or* a rare lexicon
   word that the correction out-frequencies **50×**. The ratio is what fixes
   `manus`→মানুষ and `bondu`→বন্ধু, whose typed forms are themselves lexicon
   entries.
5. **Not user-protected** (a previously kept spelling).

The constants are identical to iOS and are calibrated against the real
artifacts. `AutoInsertGate` is unit-tested for the structural rules (unknown and
completion channels never fire, protected words never fire, the frequency floor,
the 50× ratio on the `manus` case). **Android does not yet pin the engine
fingerprints or run the real-data calibration tests that iOS has**; if a
threshold must move, add them first. See [KNOWN-ISSUES.md](../KNOWN-ISSUES.md#ki-005).

## Next-word suggestions and personal learning

After a word commits, the ribbon offers next-word candidates from the bundled
n-gram model merged with a personal overlay of learned words. Learning is
engine-side and bounded. The client persists the exported snapshot in
`filesDir/personal-autosuggest.bin` and the engine validates its fingerprint on
import, so a snapshot from a different artifact generation is dropped, never
merged. Kept spellings are stored by `LearnedWordStore` (bounded, local).

The settings screen's **Privacy › Clear Learned Words** removes the snapshot and
the kept words. A running keyboard process may still hold the in-memory overlay
until it restarts; the iOS app behaves the same way.
