# Demo accounts

Created by `DemoDataSeeder` at the first startup in the `demo` profile (the [live demo](https://aerides.onrender.com/))
and in `dev`. **Every account's password is `aerides-demo`.** Dates are relative to the day the data was seeded.

## Accounts

| Email | Password | Name | Language | Groups and role | Good for testing |
|-------|----------|------|----------|-----------------|------------------|
| `giulia@example.com` | `aerides-demo` | Giulia | it | Gilda dei Draghi: **OWNER** | Everything: roles, invite code, Discord webhook, completing events, stats |
| `marco@example.com` | `aerides-demo` | Marco | it | Gilda dei Draghi: **OFFICER** | Creating and publishing events, approving sign-ups, seeing drafts |
| `luca@example.com` | `aerides-demo` | Luca | en | Gilda dei Draghi: **MEMBER** | Waitlist promotion, a pending approval, hidden drafts, the English UI |
| `sara@example.com` | `aerides-demo` | Sara | en | Gilda dei Draghi: **MEMBER** | A second member: a confirmed seat in the full raid |
| `paolo@example.com` | `aerides-demo` | Paolo | it | none | Access denied to every group; joining with an invite code |
| `elena@example.com` | `aerides-demo` | Elena | en | Night Owls: **OWNER** | Tenant isolation: she can't see Gilda dei Draghi, and Gilda's members can't see Night Owls |

The invite code is random: Giulia or Marco find it in **Gilda dei Draghi > Settings**, then Paolo joins with it.

## Events and sign-ups

| Group | Event | When | State | Slots | Sign-ups |
|-------|-------|------|-------|-------|----------|
| Gilda dei Draghi | Raid: Vault of Glass | in 3 days, 21:00 | PUBLISHED, **full** | Tank 1, Healer 1, DPS 1 | Giulia Tank **CONFIRMED**, Marco Healer **CONFIRMED**, Sara DPS **CONFIRMED**, Luca Tank **WAITLISTED** |
| Gilda dei Draghi | Serata giochi da tavolo | in 6 days, 20:00 | PUBLISHED, **requires approval** | Giocatore 5 | Luca **PENDING** |
| Gilda dei Draghi | Raid: Crota's End (bozza) | in 10 days, 21:00 | **DRAFT**: only OFFICER and OWNER see it | Tank 2, DPS 4 | none |
| Gilda dei Draghi | Raid del giovedì | 75 days ago | **COMPLETED** | Tank 1, Healer 1, DPS 3 | Giulia Tank CONFIRMED, Marco Healer CONFIRMED |
| Gilda dei Draghi | Raid del giovedì | 45 days ago | **COMPLETED** | Tank 1, Healer 1, DPS 3 | Giulia Tank CONFIRMED, Marco DPS CONFIRMED |
| Gilda dei Draghi | Raid del giovedì | 14 days ago | **COMPLETED** | Tank 1, Healer 1, DPS 3 | Giulia Tank CONFIRMED, Luca DPS **WITHDRAWN** |
| Night Owls | Gloomhaven: scenario 12 | in 5 days, 22:00 | PUBLISHED | Player 4 | Elena **CONFIRMED** |

The three completed raids feed the statistics page: Giulia attended 3 of 3, Marco 2, Luca and Sara 0, and DPS is
the most unfilled role.

## Scenarios

- **Live waitlist promotion**: log in as Luca in one browser and open "Raid: Vault of Glass"; as Giulia in another,
  withdraw from Tank. Luca is promoted and sees it without reloading, and an email is queued (logged, not sent, on
  the demo).
- **Approval**: as Marco, open "Serata giochi da tavolo" and approve or reject Luca.
- **Drafts**: "Raid: Crota's End (bozza)" is listed for Marco and Giulia, and is not found for Luca or Sara.
- **Access denied / invite codes**: as Paolo, every group URL answers 404, not 403, on purpose: "forbidden" would
  reveal that the group exists. Join Gilda with the invite code. (403 is for members whose role is too low, e.g.
  Luca trying to publish an event.)
- **Tenant isolation**: as Elena, open a Gilda dei Draghi event URL copied from Giulia's session: not found.
- **Languages**: Giulia, Marco and Paolo see Italian, Luca, Sara and Elena English (emails too).

## When the seed runs

Only when `giulia@example.com` doesn't exist yet, in one transaction: restarting never duplicates anything, and
on an existing database a newer seed does nothing. Applying it to a database that already has the old demo data
means emptying that database first.
