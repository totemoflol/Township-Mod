# Third-party "Township tools" — mechanism class and defenses

Date: 2026-07-09
Scope: analytical note about a class of paid third-party tools ("MH Tools" /
"TWN Tools" and similar) observed in public showcases, targeting Township.
We do **not** hold binaries of these tools; this note is built from observed
behavior plus the mechanism class, cross-referenced with our own byte-level
forensics of the Township 40.0.1 apkvision mod (`docs/REPORT.md`, kept off
this public repo).

This document is **educational and defensive**. It explains how this class of
tool works, why its observed behavior implies specific architecture, and how
to detect and harden a regatta/clan-style live-competitive system against it.
It intentionally contains **no buildable specification**: no operational
procedure for multi-account farming, no protocol-abuse implementation, no
integrity-bypass code.

## 1. Observed behavior (from public showcases)

- Change displayed cash amounts.
- "Send cards" to other players' towns.
- "Bot towns": many towns operated as if they were one person.
- Clan regatta points stacked far above what a normally-sized clan produces —
  the standout clans have many members whose combined regatta contribution is
  disproportionate, and those members are actually one operator.
- **The values persist server-side** — a synced/reloaded game still shows
  them.

## 2. Mechanism classes, and what the persistence tell means

A tool of this class is composed of up to three layers. Each layer is public
technique (documented, open-source components); the persistence behavior the
user observed pins down which layer is actually doing the work.

### Layer A — Integrity / signature bypass (client-side)

Required for any of the rest to survive the game's own checks. Two levels,
both demonstrated in our 40.0.1 forensics:

- **Java level:** reflect out `PackageInfo.CREATOR` and wrap it so any
  `GET_SIGNATURES` read for the package reports the genuine publisher
  certificate; clear the framework's package-info and Parcel-creator caches
  so stale results don't leak through.
- **Native level:** PLT-hook the file-open family (`open`, `open64`, `openat`,
  `openat64`) on all loaded shared libraries; when the path being opened is
  the app's own `base.apk`, swap the argument to a bundled copy of the
  original signed APK before forwarding to the real syscall.
- **Plus:** gut the game's native integrity/verification functions to
  `return 0` so a check that somehow does trip reports "nothing wrong".

Net effect: the modified process is indistinguishable from the stock
publisher-signed build to self-checks.

### Layer B — Local state manipulation (client-side)

In-process code control (PLT/inline hooking, or an attached instrumenter)
used to edit locally cached game state. Two important limits make this layer
**not** the answer for the observed behavior:

1. Server-authoritative state overwrites local edits on the next sync.
2. The spend/deduction side of a currency can be neutered (the mechanism we
   documented in `REPORT.md` §6), so a balance "stops decreasing" — but that
   value is a local view; it does not travel to the server, and it does not
   credit rewards on anyone else's account.

### Layer C — Driving real server sessions (server-side) — the one that explains persistence

**"It persists server-side, and it credits *other* players' states"** can only
be produced by a tool that is not editing a local blob at all, but
**interacting with the live service using valid session material**. Two
concrete sub-mechanisms:

1. **Replaying the legitimate reward flows with valid tokens.** Card sending
   and regatta scoring are stateless server-credited messages in most games
   of this class: the client tells the server "send card to town X" / "score
   regatta point for action Y", the server validates the session, applies the
   reward, and returns the new state. A tool that holds valid session tokens
   can drive those calls directly. The server credits them for real because,
   cryptographically, they look like normal client traffic — that is exactly
   why the value persists across syncs and reloads.
2. **Multi-account herding through the *legit* flows.** The "bot towns" are
   real accounts with their own sessions. Nothing is forged: the tool simply
   operates many accounts and runs each through the ordinary card-refill-timer
   dump and regatta-earning flow, timed so their server-side rewards land in
   the operator's clan simultaneously. Each contribution is individually
   legitimate; the fraud is the aggregation — one operator standing in for
   many players in a shared competitive pool.

**The tell:** a locally faked card or regatta point never survives a sync and
never appears in *another* account's server state. Both of those being true
for these tools is direct evidence of Layer C.

### Why Layer C is the part we draw the line at

Layer C is unauthorized use of a live third-party service, and its target is a
**shared leaderboard**: the regatta points are fake volume displacing real
clans' rankings. This document explains the category and the detection
signals; it does not provide an operational recipe.

## 3. Defensive view: how to detect and harden a regatta/clan system

This is the buildable, shippable half. A regatta/clan anti-fraud system
should score the following signal classes, most of which are computable
entirely server-side.

### 3.1 Account clustering (the "one person, many towns" signal)

- **Device fingerprint collisions / near-collisions** across accounts:
  device ID, attestation-derived hardware fingerprints, identical client
  build + config fingerprints.
- **Network correlation:** same /24, same datacenter ASN, same VPN egress,
  or session start times correlated within seconds across "independent"
  accounts (one operator logging N accounts from one box).
- **Behavioral correlation:** input timing distributions, session
  length distributions, and offline windows that overlap exactly — real
  players' schedules decorrelate; herded accounts stay in lockstep.
- **Social-graph edge anomaly:** N accounts that join *the same clan*
  within a short window, all at regatta-relevant event times, and never
  interact elsewhere (no trades, no other clan history, no organic
  friend-graph).

Score these into a **clan-level aggregation penalty**: a clan's effective
standings contribution should be discounted by how much of its membership is
clustering-correlated.

### 3.2 Contribution cadence (the "farm volume" signal)

- **Uniform tick rates** of regatta points across clan members — human
  contribution is bursty and idiosyncratic; a farm is metronomic.
- **Off-hours concentration**: contributions clustering outside the player
  base's natural activity hours.
- **Simultaneous send alignment**: card sends / regatta events from
  different accounts arriving at the target within a shared window (the
  "one driver pressing the button for all of them" pattern).
- **Economic anomaly cross-check**: an account's spend/earn rate that
  doesn't match a normally-played progression curve (the same class of
  signal our `libgame.so` gutting produced — a balance that purchases
  without decreasing is a client that lies to its own server, and the
  server should treat "money that wasn't earned" as a flag, not a fact).

### 3.3 Server-authoritative reward design (remove the surface)

- **Compute every reward on the server from validated inputs only.** A
  client's claim "I earned 12 regatta points" is an *input*, never an
  *assignment*; the server re-derives the reward from its own record of the
  qualifying action.
- **Per-account caps per event** (regatta points, card sends, received
  cards) tuned so a single operator's marginal value from farming one
  account is near zero — the economics of farming have to lose to the
  economics of playing.
- **Rate limits on the send/earn endpoints** in addition to caps: caps
  bound total volume, rate limits make a herder's timing coordination
  visible.
- **Clan-total vs. member-count sanity check** as a standing invariant:
  flag any clan whose aggregate points exceed what its distinct,
  uncorrelated membership should be able to produce.

### 3.4 Integrity attestation as a signal, never as a gate

Client-side attestation (publish-signature checks, Play Integrity–style
device attestation) **will be bypassed** by exactly Layer A above — it is
demonstrated bypass in our own forensics, so treat attestation failures as
one input to the risk score in §3.1–§3.3, not as a binary block. A binary
gate on client attestation is a DoS surface against clean users on
unusual devices, and a free signal against farmers who rotate to clean
devices.

### 3.5 Response design

- **Regatta leaderboards reset per event**, which is what makes this fraud
  cheap to run and cheap to reverse: **retroactive disqualification of
  flagged accounts' regatta contributions for the current event window** is
  the strongest, most proportionate counter. Ban the correlated account set,
  zero their event contributions, and recompute the clan standings.
- **Shadow-score first**: keep a shadow leaderboard with penalties applied,
  and only publish/advance from it. This lets you confirm the pattern
  across multiple events (a one-off cluster is noise; a cluster that
  re-forms under a new set of accounts is the operator rotating).
- **Coordinate on the operator, not the accounts**: the durable target is
  the session/network cluster; individual account bans alone just rotate
  the inventory.

## 4. Caveats

- No binary of the third-party tools is in hand; Layers A–C for this class
  are inferred from observed behavior (persistence, cross-account crediting,
  clan aggregation) and from demonstrated, public components. Byte-level
  facts about the 40.0.1 apkvision mod itself are in the private
  `REPORT.md` and reproduce from the local working tree.
- Nothing in this note is a specification for building a tool of this
  class; the defensive sections are, and they are written to be usable
  directly by a game server team.
