# Parity with the Weather Lab Windows app

Scope list, added October 25, 2026. Each item gets a mockup first, then a build, a What's New entry, and a PR that waits for "merge". Work goes in this order, one item at a time.

Windows reference: Weather Lab 0.37.19 (https://github.com/TheDutchMan13/Weather-Lab).

| # | Item | Windows has | Android has now | Status |
|---|------|-------------|-----------------|---|
| 1 | Records and analysis (renamed from Records) | Group chips (Records, Heat, Rain and storms, Climate and comparisons), summary tiles, Extreme events lists, Records board, Distribution chart | A plain list of all-time records | Done: Android 0.7.7, Weather Lab 0.37.20 |
| 2 | Heat safety (renamed from Heat Safety) | Hours at Danger or worse, "you are here" level ladder, hours in each level today, dangerous heat days | Current level, level list, hot-day counts | Done: Android 0.7.8, Weather Lab 0.37.21 |
| 3 | Daily (renamed from Daily History) | One page with 1 day, 7 days, 30 days and All, summary tiles, charts, wind rose, rain totals | Yesterday or one day of the last 60 | Done: Android 0.7.9, Weather Lab 0.37.22 |
| 4 | Live trend charts | 24-hour line with low and high on the Air and Wind and rain cards | Plain numbers on those cards | Done: Android 0.7.10, Weather Lab 0.37.23 |
| 5 | Dew point card | Card at the bottom of Live with a 24-hour trend | Dew point as text under feels like | Done: Android 0.7.11 |
| 6 | Wind (renamed from Wind Rose) | Bands 1, 2, 3-4 and 5+ mph, calm shown separately | Draws the bands the PC sends; check the calm percentage | Done: Android 0.7.12, Weather Lab 0.37.24 |
| 7 | Question-mark help icons | Removed everywhere | Skipped on purpose: the Android app keeps its ? icons, only the Windows app dropped them | Skipped by request |
| 8 | More menu regroup | Now, History, Air sun and heat, Forecast and storms, Season, Tools, Health, Settings; Station becomes Sensors and Health | Weather, Rain and storms, History, App | Done: Android 0.7.13 |
| 9 | What's New and version | An entry per feature | Entries for 0.7.7 to 0.7.13 added, one per item | Done: every item above has a What's New entry and its own version |

Some items need new endpoints in the Windows app's read-only API (for example, event lists and the heat-level hours). Those are added to Weather Lab first.

All items finished October 25, 2026. Android builds are still to be compiled and tagged from Android Studio (v0.7.7 to v0.7.13).
