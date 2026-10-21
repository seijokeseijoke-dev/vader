# Väder 6 dagar

Visar SMHI:s 6-dagarsprognos för valfri ort i Norden, sammanfattad i klartext.

- **Webbversion (för test):** https://seijokeseijoke-dev.github.io/vader/
- **Android-APK:** se *Releases* (byggs automatiskt vid varje push till `main`)

## Funktioner
- Inmatning av ort, med "Menade du"-förslag när det finns flera orter med samma namn
- 6 dagar i rutnät, 3 i bredd (2 rader), eller 2 i bredd (3 rader) på smala skärmar: symbol, högsta och lägsta temperatur
- **Sammanfattning i klartext:** när det regnar/snöar och hur mycket, samt frostrisk
- **Markeringar:** Frost, Blåsigt (byar ≥ 15 m/s eller medelvind ≥ 10 m/s), Mycket regn/snö (≥ 10 mm), Åska, Osäker (dag 5–6)
- Tryck på en dag för att se timprognosen
- Appen minns senast valda ort. Android har även pull-to-refresh

## Datakällor
| | Källa | Licens |
|---|---|---|
| Prognos | SMHI Open Data, modell **SNOW1gv1** (ersatte PMP3g som stängdes 31 mars 2026) | CC BY 4.0, kommersiell användning tillåten |
| Ortsökning, Android | Androids inbyggda `Geocoder` | Ingen nyckel |
| Ortsökning, webb | OpenStreetMap Nominatim | ODbL. Endast för test, inte för tung trafik |

`https://opendata-download-metfcst.smhi.se/api/category/snow1g/version/1/geotype/point/lon/{lon}/lat/{lat}/data.json`

Prognosen är timvis i cirka 2 dygn, därefter i 2-, 6- och 12-timmarssteg, totalt cirka 10 dygn. Nederbörden (`precipitation_amount_mean`) anger mängd i mm för intervallet `intervalParametersStartTime → time` och summeras per dygn efter intervallets mittpunkt.

## Så beräknas "smart"
- **Dagens symbol:** Tas från dagtid (07–20). En torr dag får median-molnigheten. En våt dag får den vanligaste nederbördssymbolen, och vid lika antal den kraftigaste.
- **Osäkerhet:** Dag 5–6 visas nedtonade. Träffsäkerheten sjunker tydligt efter 4–5 dygn.

## Struktur
```
web/index.html                      Webbversion (fristående, publiceras via GitHub Pages)
app/src/main/java/se/vader/app/
├── MainActivity.kt
├── data/Smhi.kt                    Retrofit + modeller för SNOW1g
├── data/Places.kt                  Ort → koordinater (Geocoder)
├── domain/Forecast.kt              Dygnsaggregering, flaggor, sammanfattning (ren Kotlin, enhetstestad)
└── ui/                             Compose: WeatherScreen, WeatherIcon, WeatherViewModel
app/src/test/.../ForecastBuilderTest.kt
```

## Bygga
`./gradlew testDebugUnitTest assembleDebug`. CI kör samma kommandon och publicerar APK:n som en Release.

## Inför Google Play
1. Byt `applicationId` från `se.vader.app` till ett eget före första uppladdningen
2. Skapa en riktig ikon, en integritetspolicy och ett signerat AAB (`bundleRelease`)
3. Ange "Data: SMHI" i appen och i butikstexten, eftersom CC BY kräver det
4. Geocoder kräver Google Play-tjänster. På telefoner utan dem fungerar inte ortsökningen
