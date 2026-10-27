# Screenshots

Images referenced by the main README. To capture them with realistic data:

1. Start PostgreSQL and Mailpit (`docker compose up -d postgres mailpit`) on an empty database.
2. Start the backend with `SPRING_PROFILES_ACTIVE=dev`: the demo data seeder creates the "Gilda dei Draghi" group.
3. Start the frontend (`npm start`) and log in as `giulia@example.com` / `aerides-demo`.

| File | Page |
|------|------|
| `event-detail.png` | "Raid: Vault of Glass": full tank slot with Luca on the waitlist |
| `calendar.png` | Events page, calendar view |
| `stats.png` | Statistics of the group (three completed raids) |
| `event-form.png` | New event, with two slots and "Repeat every week" checked |
