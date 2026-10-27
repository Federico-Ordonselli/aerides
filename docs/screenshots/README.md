# Screenshots

Images referenced by the main README, taken from the local stack with the demo data, logged in as `giulia@example.com` / `aerides-demo`, English UI, desktop at 1280 px wide and phone at 390 px, device scale 2.

To get the same data:

1. Start PostgreSQL and Mailpit (`docker compose up -d postgres mailpit`) on an empty database.
2. Start the backend with `SPRING_PROFILES_ACTIVE=dev`: the demo data seeder creates the "Gilda dei Draghi" group.
3. Start the frontend (`npm start`), log in and open the pages below.

| File | Page |
|------|------|
| `event-detail.png` | "Raid: Vault of Glass": full tank slot with Luca on the waitlist |
| `calendar.png` | Events page, calendar view |
| `stats.png` | Statistics of the group (three completed raids) |
| `event-form.png` | New event, with three slots and "Repeat every week" checked (full page) |
| `mobile-event-detail.png` | "Raid: Vault of Glass" on a phone |
