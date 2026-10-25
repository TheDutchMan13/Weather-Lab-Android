# Parity with the Weather Lab Windows app

Scope list, added October 25, 2026. Each item gets a mockup first, then a build, a What's New entry, and a PR that waits for "merge". Work goes in this order, one item at a time.

Windows reference: Weather Lab 0.37.19 (https://github.com/TheDutchMan13/Weather-Lab).

| # | Item | Windows has | Android has now |
|---|------|-------------|-----------------|
| 1 | Records and analysis (renamed from Records) | Group chips (Records, Heat, Rain and storms, Climate and comparisons), summary tiles, Extreme events lists, Records board, Distribution chart | A plain list of all-time records |
| 2 | Heat safety (renamed from Heat Safety) | Hours at Danger or worse, "you are here" level ladder, hours in each level today, dangerous heat days | Current level, level list, hot-day counts |
| 3 | Daily (renamed from Daily History) | One page with 1 day, 7 days, 30 days and All, summary tiles, charts, wind rose, rain totals | Yesterday or one day of the last 60 |
| 4 | Live trend charts | 24-hour line with low and high on the Air and Wind and rain cards | Plain numbers on those cards |
| 5 | Dew point card | Card at the bottom of Live with a 24-hour trend | Dew point as text under feels like |
| 6 | Wind (renamed from Wind Rose) | Bands 1, 2, 3-4 and 5+ mph, calm shown separately | Draws the bands the PC sends; check the calm percentage |
| 7 | Question-mark help icons | Removed everywhere | Check whether TermHelp still shows them; remove if so |
| 8 | More menu regroup | Now, History, Air sun and heat, Forecast and storms, Season, Tools, Health, Settings; Station becomes Sensors and Health | Weather, Rain and storms, History, App |
| 9 | What's New and version | An entry per feature | Newest entry is 0.7.6; each item adds an entry and a version bump |

Some items need new endpoints in the Windows app's read-only API (for example, event lists and the heat-level hours). Those are added to Weather Lab first.
