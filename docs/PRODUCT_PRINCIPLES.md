# Syrmos is a companion, not a schedule

This is the product north star. Every feature, screen, and PR gets measured against it. When a change makes Syrmos feel more like a timetable you have to read, it is moving the wrong way. When it makes Syrmos feel like something that already did the thinking for you, it is moving the right way.

A schedule app answers "what time do the trains run?" It hands you a table and leaves the interpretation to you. You open it, query, read, decide, close. The work sits with the user.

A calm mobility companion answers "what should I do right now, and will I be okay?" It does the interpretation and it reaches out before you ask.

The architecture is already companion-grade: offline prediction, live positions, the 1s simulator keeping trains alive with no signal, last-departure awareness. The job is to let that intelligence show through the surface instead of hiding it behind another grid.

## The four rules

### 1. Answer-first, not table-first
Lead with one sentence the user can act on: "Next M2 to Syntagma, 4 min, left platform." The full timetable still exists, one tap down, for the moments someone wants it. The table is a detail view, never the homepage.

Litmus test: can the user get the answer without reading a grid? If not, the answer is buried.

### 2. Proactive, not pull
A schedule app waits to be opened. A companion shows up where the user already is: the widget, the Lock Screen, the Live Activity, "you must leave by 21:12." Every surface that delivers the answer without the user opening the app moves Syrmos toward companion.

Litmus test: does the user have to open the app to learn this? If the answer matters and the answer is knowable, push it.

### 3. Reassuring, not just informative
Calm is mostly the app removing anxiety the user already carries: am I on the right platform, did I miss it, is the last train gone, does this still work underground. The offline-alive indicator is not a status icon, it is the app saying "keep walking, I have you." The confidence score is not an ETA, it is "you'll make it, relax."

Litmus test: does this feature reduce a worry, or just present a fact? Prefer the one that reduces the worry.

### 4. Low-decision, smart defaults
GPS already knows where you are. The app should collapse "pick a station, pick a line, pick a direction" into "from here, your trains are these." Every tap removed moves Syrmos further from tool and closer to companion. A mode the user has to choose is a decision we failed to make for them. Bilingual support adjusts density quietly; it is not a tourist toggle.

Litmus test: how many taps and choices stand between launch and the answer? Drive that number down.

## How to use this doc

Before merging a feature, ask the four litmus questions. A feature can be technically excellent and still drift back toward "a nicer table." That drift is the failure mode this doc exists to catch.

When two implementations are equally cheap, ship the one that is more answer-first, more proactive, more reassuring, or lower-decision. When a feature cannot satisfy any of the four, question whether it belongs.

The companion framing is also the marketing story. The hardest engineering in Syrmos (full offline prediction) is currently invisible. Making it visible is not scope creep; it is the product finally looking like what it already is.

## Addendum: widget philosophy (1.2 "Widgets Everywhere")

Widgets are the purest test of the four rules: no navigation, no scroll, one glance. A widget that makes you open the app has failed. The 1.2 milestone puts the offline-prediction intelligence on every home screen, lock screen, and wrist. Four constraints govern every widget surface:

- **Single-glance.** The answer must land in under a second. One headline number ("3 min"), one line, one destination. Everything else is supporting detail, sized down.
- **No scroll.** A widget never scrolls. If content does not fit the family, it is the wrong family. Larger families show more rows; they do not hide rows behind a scroll.
- **Line-tinted.** Every surface derives its accent from the tracked line (M1 green, M2 red, M3 blue, tram orange, suburban purple), from a single token source. Color is information, not decoration.
- **Dark-first.** Widgets are designed for the dark home screen and the always-on lock screen first, then verified in light. Liquid Glass (`.regularMaterial`) reads on both; tinted StandBy and accented lock-screen modes must stay legible.
