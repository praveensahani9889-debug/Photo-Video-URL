# Account types (Free / Premium 49 / Premium 399 / team)

The plan of a user is read from Firebase Realtime Database: `accounts/<Account ID>`.
Only you can write there (Firebase Console). The app can only read it, so nobody can upgrade himself.

**Account ID** = the user's "Account ID" (Settings > Premium plans page, copy button). It is the same ID as in `user/<Account ID>`.

No entry = **Free**.

## How to give a plan
Firebase Console > Realtime Database > Data > `accounts` > Add child `<Account ID>` with:

| field          | value                                                                                  |
|----------------|----------------------------------------------------------------------------------------|
| `type`         | `free`, `premium_49`, `premium_399`, `friend`, `moderator`, `admin`                    |
| `purchaseDate` | text, e.g. `26 Nov 2026` (shown in Settings; use it to not waste anybody's subscription)|
| `expiresAt`    | optional, milliseconds (e.g. 1796000000000). After it a paid plan counts as Free again. |

Example:
```json
{ "type": "premium_49", "purchaseDate": "26 Nov 2026", "expiresAt": 1798588800000 }
```

Badges: Free none, 49 Silver, 399 Gold, Friend, Moderator, Admin.
`friend`, `moderator`, `admin` get everything of 399, plus unlimited Supabase projects and no waiting time for websites.
`expiresAt` only counts for `premium_49` and `premium_399`; team accounts never expire.

## Limits
| | Free | 49 | 399 |
|---|---|---|---|
| Photos / day | 3 | 10 | unlimited |
| Videos / day | 2 | 5 | unlimited |
| Documents / day | 1 | 3 | unlimited |
| APKs / day | 1 | 2 | unlimited |
| Files / day | 1 | 2 | unlimited |
| Text posts / day | 5 | 10 | unlimited |
| Website | 1 per 36 h | 1 per 12 h | 1 per 6 h |
| Advanced options | no | yes | yes |
| Address endings (.com .in ...) | no | yes | yes + own ending |
| Supabase projects | 1 | 2 | 5 |
| Ads | yes | yes | no |

The limits are in `data/model/AccountPlan.kt` (`Plans`).

NOTE: the limits are counted on the phone. This is fine for a first version; real enforcement needs a server (later).
