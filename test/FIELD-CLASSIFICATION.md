# Township save field classification

Source files:
- `test/LocalInfo.decoded.xml`
- `test/mGameInfo.decoded.xml`

This document records observations from the decoded save snapshot. It distinguishes saved fields from conclusions about what the live game will accept. A local XML value is not proof that the server will honor an edit.

## Card Collections

Location: `DataStoreCollection/GameEvents/CardCollections/DataLogic`

### OwnedCards structure

Each serialized entry has:
- `cardId`
- `generatedCount`
- `inStockCount`
- `isNew`
- `maxInStockCount`

Snapshot observations:
- 159 entries are present.
- IDs present: `card_01` through `card_148`, then `card_150` through `card_160`.
- `card_149` does not occur anywhere in `mGameInfo.decoded.xml`.
- Every listed entry has `generatedCount=3`, `inStockCount=3`, `maxInStockCount=3`, and `isNew=false`.
- `lastSeenCollectionProgress=149`.
- `pinnedCardCollectionsBalanceId=CardC8_Balance`.
- `LastSeenSetProgress` contains set_01 through set_14 at 10, and set_15 at 9.
- The set-completion flags set_01 through set_14 are true.

### Important interpretation / unresolved card_149 gap

The file proves that the `card_149` record is absent from this snapshot. It does **not** explain why. The nearby IDs are present, but the XML contains no explicit tombstone, disabled flag, replacement mapping, or explanatory comment for ID 149. `lastSeenCollectionProgress=149` is a separate progress field; it is not evidence that a `card_149` record should exist.

Possible explanations such as a reserved/retired ID, a content/configuration gap, or a collection-version mismatch are hypotheses only. Distinguishing them requires the matching event balance/configuration or another save from a different event/version.

### Do not overstate ownership

Although the entries are under `OwnedCards` and each has `inStockCount=3`, this snapshot alone does not prove that 159 distinct cards are genuinely collected or that changing these values will persist. The same event's Amplitude tracking component reports:
- `trackedUniqueCollectedCards=4`
- `trackedMaxCollectedCards=4`
- `trackedTokensCount=240224`

Those analytics counters conflict with a literal interpretation of all 159 entries as user-collected cards. Treat `OwnedCards/inStockCount` as the strongest saved per-card quantity candidate, but flag the ownership semantics as unverified. The event also stores `TradeLogic/currentTokens=241280`, `lastSeenTokens=241280`, and `additionalTokens=240224`; these are separate token fields and should not be conflated.

## Currency and progression

Location: `Global/Var`

| Field | Snapshot value | Classification |
|---|---:|---|
| `money` | 530 | Coins balance candidate |
| `moneyCash` | 25545 | Cash / T-Cash balance candidate |
| `experience` | 3436 | Experience |
| `levelup` | 10 | Town level |
| `PigBank_Cash` | 100 | Piggy-bank value; exact live behavior not established |

Cross-check: `LocalInfo` contains an `HSCashOp` history whose final recorded `AddCash` operation results in 25545, consistent with `moneyCash`.

## Profile cosmetics

Location: `PlayerProfile/Configs`, plus `Global/Var`.

- `MyBadge=badge_08_grand` is the saved selected-badge value.
- `UnlockedBadges` contains two encoded entries.
- `UnlockedFrames`, `UnlockedStyles`, `UnlockedThemes`, and `UnlockedExpRanks` are the corresponding saved unlock-list fields; in this snapshot the latter four lists are empty.
- No explicit `OwnedAvatars` or `UnlockedAvatars` list was found in either decoded file.
- Avatar IDs in `LocalInfo`'s cached other-player profile component are profile-cache data, not proof of the local player's owned-avatar inventory.
- The cached self-profile badge/avatar values do not agree cleanly with `mGameInfo`'s `MyBadge`; prefer the dedicated local config field for describing the saved selected badge, while recording the mismatch.

## Other resource/count candidates

| Field / location | Snapshot value | Classification / caveat |
|---|---:|---|
| `coupons/coupon` entries | HireDealer 12; LoadTrainAirport 19 | Coupon counts |
| `Global/Var/Brick`, `Glass` | 1 each | Construction-material count candidates |
| `Global/Var/match3Life` | 5 | Match-3 lives |
| `Global/Var/m3RainbowBall` | 12 | Match-3 booster count |
| `Global/Var/m3HorizontalLine`, `m3Mallet`, `m3Reshuffle`, `m3VerticalLine` | 2 each | Match-3 booster counts |
| `Global/Var/expeditionEnergy` | 197 | Expedition energy |
| `RetroCarCollection` entries | 6 with `isBought=true` | Purchase-state flags; this snapshot does not map each flag to a specific car |

`LocalInfo/ProductBalance` stores bread=2, milk=3, wheat=3, but the snapshot does not establish whether this is the authoritative warehouse inventory. Product-named counters elsewhere may be analytics/task counters.

## Reward definitions versus current state

Reward tables, reward bundles, and pending reward definitions are not equivalent to current balances or owned inventory. For analysis, prioritize fields explicitly named as balances, counts, owned records, or unlocked lists, and label semantics as unverified where the file does not prove them.

## Confidence summary

- **Directly observed:** XML paths, field names, literal saved values, absence of `card_149` from this file, and the mismatching card analytics counters.
- **Strong interpretation:** `moneyCash` is the cash balance; `inStockCount` is the per-card quantity field; coupon and Match-3 fields are resource-count candidates.
- **Unverified:** whether editing any local field is accepted by the live game/server; why `card_149` is absent; whether all `OwnedCards` entries represent actual collected cards; whether an avatar ownership list exists outside these two files.
