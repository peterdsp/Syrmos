# Explore V2 image requirements

The SVG drafts use vector illustrations, so no photos are required to review the layout. Real images are needed before the final production pass.

## First delivery: eight essential destination images

Please provide one image for each:

1. Meteora and Kalambaka, ideally including the landscape and monastery
2. Thessaloniki, preferably the waterfront, White Tower, or railway arrival context
3. Larissa, city center or railway station context
4. Piraeus port and waterfront
5. Athens Airport exterior or recognizable terminal detail
6. Monastiraki with an Athens landmark
7. Diakopto or the Odontotos rack railway
8. Patras waterfront, station, or Rio context

## Second delivery: collection imagery

One representative image for each collection is enough for the first release:

- beach reachable by rail
- mountain or scenic railway
- island with a clearly disclosed ferry connection
- day trip
- museum or archaeological site
- stadium
- university area
- airport

## Optional event imagery

- Olympic Stadium exterior
- Karaiskakis Stadium exterior
- a rights-cleared concert or festival image
- Piraeus cruise or ferry arrival
- museum late-opening image

Event posters should be used only if Syrmos has permission and their dates remain legible after cropping.

## RailPulse imagery

RailPulse requires no promotional photography for the MVP. Use structured icons and existing vehicle or station artwork.

Community photo uploads stay outside the privacy-preserving RailPulse scope. RailPulse uses structured signals and icons only. User-submitted photos are not design assets to request in advance.

## Brand and transport assets

- Ariadne owl as SVG, PDF vector, or transparent PNG at 1024 by 1024 or larger
- current Syrmos app icon as vector or 1024 by 1024 PNG
- vehicle artwork for Athens Metro, Tram, Suburban, Intercity, Thessaloniki Metro, Patras Suburban, and Odontotos
- optional station entrance photos for the most important hubs

Repository icons should be reused when they already satisfy these needs. Do not redraw official operator logos unless usage rights are clear.

## Technical specification

- preferred master: original JPEG, HEIC, PNG, or TIFF
- minimum hero size: 2000 by 1250 pixels
- minimum card size: 1200 by 900 pixels
- color: sRGB
- no text, watermark, border, or baked-in gradient
- avoid images already aggressively sharpened or filtered
- keep the important subject inside the center 60 percent so 16:9, 3:2, and square crops all work
- include photographer, source URL, license, and required credit for every image
- provide light and dark variants only when the image fails under a dark overlay

Suggested folder structure:

```text
assets/explore/
  destinations/
  collections/
  events/
  vehicles/
  brand/
  credits.csv
```

Use short stable asset keys such as `meteora_sunset_01`, not localized filenames.

## Product decision needed

Confirm whether `Explore Greece` is:

1. rail-only, with walking and official rail-replacement buses, or
2. rail-first, with optional bus and ferry connectors for destinations the railway does not reach directly.

The drafts assume option 2, but visually separate every connector so Syrmos never presents it as a direct train journey.
