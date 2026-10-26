#!/usr/bin/env python3
"""Fills a fresh Raidboard deployment with demo data, through the public REST API only.

    python3 deploy/render/seed-demo.py https://raidboard.onrender.com

Creates three users (password "raidboard-demo"), one group and a few events that show the main
flows: a full slot with a waitlist (withdraw as Giulia to see Luca promoted live), an event that
needs approval, and a draft only officers can see. Uses only the standard library.
Run it once on an empty database: it stops if the demo group already exists.
"""
import json
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

PASSWORD = "raidboard-demo"
USERS = {
    "giulia": ("giulia@example.com", "Giulia", "it"),
    "marco": ("marco@example.com", "Marco", "it"),
    "luca": ("luca@example.com", "Luca", "en"),
}
GROUP_NAME = "Gilda dei Draghi"


def call(base, method, path, token=None, body=None):
    request = urllib.request.Request(base + path, method=method,
                                     data=json.dumps(body).encode() if body is not None else None)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request, timeout=90) as response:  # a sleeping free instance is slow to wake
            text = response.read().decode()
            return json.loads(text) if text else None
    except urllib.error.HTTPError as e:
        raise SystemExit(f"{method} {path} -> {e.code}: {e.read().decode()}")


def token_for(base, key):
    email, name, locale = USERS[key]
    try:
        return call(base, "POST", "/api/auth/register", body={
            "email": email, "password": PASSWORD, "displayName": name, "locale": locale})["accessToken"]
    except SystemExit as e:
        if "409" not in str(e):
            raise
        return call(base, "POST", "/api/auth/login", body={"email": email, "password": PASSWORD})["accessToken"]


def at(days, hour):
    """A start time `days` from today at `hour`:00 Italian time (UTC+1 in winter; good enough for a demo)."""
    day = datetime.now(timezone.utc).date() + timedelta(days=days)
    return datetime(day.year, day.month, day.day, hour - 1, 0, tzinfo=timezone.utc).isoformat().replace("+00:00", "Z")


def event(title, starts_at, slots, requires_approval=False, description=None):
    return {"title": title, "description": description, "startsAt": starts_at, "durationMinutes": 180,
            "requiresApproval": requires_approval, "withdrawDeadlineHours": 2, "timeZone": "Europe/Rome",
            "slots": [{"roleName": role, "capacity": capacity} for role, capacity in slots]}


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    base = sys.argv[1].rstrip("/")
    giulia, marco, luca = (token_for(base, key) for key in ("giulia", "marco", "luca"))

    if any(group["name"] == GROUP_NAME for group in call(base, "GET", "/api/groups", giulia)):
        raise SystemExit("Demo data already there: nothing to do.")
    group = call(base, "POST", "/api/groups", giulia,
                 {"name": GROUP_NAME, "description": "Raid del giovedì e serate di gioco da tavolo."})
    g = group["id"]
    for token in (marco, luca):
        call(base, "POST", "/api/groups/join", token, {"inviteCode": group["inviteCode"]})
    marco_id = next(m["userId"] for m in call(base, "GET", f"/api/groups/{g}/members", giulia)
                    if m["displayName"] == "Marco")
    call(base, "PATCH", f"/api/groups/{g}/members/{marco_id}", giulia, {"role": "OFFICER"})

    def publish(token, body):
        created = call(base, "POST", f"/api/groups/{g}/events", token, body)
        return call(base, "POST", f"/api/groups/{g}/events/{created['id']}/publish", token)

    def sign_up(token, published, slot_index):
        call(base, "POST", f"/api/groups/{g}/events/{published['id']}/signups", token,
             {"slotId": published["slots"][slot_index]["id"]})

    # Full tank slot: Giulia confirmed, Luca waitlisted. Withdraw as Giulia and Luca is promoted live.
    raid = publish(giulia, event("Raid: Vault of Glass", at(3, 21), [("Tank", 1), ("Healer", 2), ("DPS", 3)],
                                 description="Portate le pozioni."))
    sign_up(giulia, raid, 0)
    sign_up(luca, raid, 0)
    sign_up(marco, raid, 1)

    # Needs approval: Luca's request waits for an officer
    board_games = publish(marco, event("Serata giochi da tavolo", at(6, 20), [("Giocatore", 5)],
                                       requires_approval=True))
    sign_up(luca, board_games, 0)

    # A draft: officers and the owner see it, Luca doesn't
    call(base, "POST", f"/api/groups/{g}/events", marco,
         event("Raid: Crota's End (bozza)", at(10, 21), [("Tank", 2), ("DPS", 4)]))

    print(f"Demo data created in group {g}. Users: "
          + ", ".join(email for email, _, _ in USERS.values()) + f" / password: {PASSWORD}")


if __name__ == "__main__":
    main()
