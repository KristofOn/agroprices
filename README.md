# Agroprijzen widget

Android homescreen-widget (Nothing 4a Pro) met:
- Graanprijzen (tarwe, gerst, mais) van [fegra.be](https://fegra.be), t.o.v. de vorige dag.
- Runderkarkasprijzen koeien, categorie DU en DE, van [landbouwcijfers.vlaanderen.be](https://landbouwcijfers.vlaanderen.be), t.o.v. de vorige week.

## Structuur

- `app/` -- de Android-app (Kotlin, Gradle). Haalt granen rechtstreeks bij fegra.be op; haalt runderkarkasprijzen op bij een klein JSON-bestand dat via GitHub Pages gepubliceerd wordt.
- `backend/` -- Node.js/Playwright-scraper die wekelijks `docs/beef.json` genereert.
- `docs/` -- output van de scraper, gepubliceerd via GitHub Pages.
- `.github/workflows/scrape-beef.yml` -- GitHub Actions: draait de scraper elke donderdag (+ vrijdag-vangnet), commit enkel bij wijziging.

## Backend lokaal draaien

```
cd backend
npm install
npx playwright install chromium
npm test          # unit tests (parsing/validatie), geen browser nodig
npm run scrape     # echte scrape, schrijft ../docs/beef.json
```

Getest tegen de live site op 24/08/2026 -- werkt, inclusief het wegklikken van de cookiebanner die anders de downloadknop blokkeert.

## Android-app bouwen

Deze werkomgeving heeft geen Android SDK/Gradle/`adb` -- de code is hier volledig geschreven, maar bouwen/installeren gebeurt bij jou:

1. Open de map `app/` in Android Studio. Android Studio genereert bij het openen automatisch de ontbrekende `gradlew`/`gradle-wrapper.jar`-bestanden (die kunnen in deze omgeving niet gegenereerd worden zonder Gradle/internet). Heb je liever de command line: installeer Gradle lokaal en run eenmalig `gradle wrapper` in `app/` om die bestanden zelf aan te maken.
2. **Voor je bouwt**: pas `app/app/src/main/kotlin/be/agroprices/widget/Config.kt` aan met je echte GitHub Pages-URL (zie hieronder) -- staat nu op een placeholder.
3. Sluit de Nothing 4a Pro aan via USB, USB-debugging aan, `adb devices` ter controle.
4. Build & installeer via Android Studio's Run-knop, of `./gradlew installDebug` vanaf `app/`.
5. Voeg de widget toe: lang drukken op het homescreen -> Widgets -> "Agroprijzen".
6. Unit tests (geen toestel nodig): `./gradlew test` vanaf `app/`.

## GitHub-repo instellen (voor de runderkarkas-scraper)

1. Maak een nieuwe GitHub-repo aan en push deze projectmap ernaartoe.
2. Settings -> Pages -> Deploy from a branch -> branch `main`, map `/docs`.
3. Settings -> Actions -> General -> Workflow permissions -> **Read and write permissions** (nodig zodat de workflow `docs/beef.json` kan committen).
4. Actions-tab -> "Scrape runderkarkasprijzen (koeien DU/DE)" -> Run workflow, om een eerste run te forceren en te controleren dat alles werkt (i.p.v. te wachten tot donderdag).
5. Noteer de Pages-URL (`https://<gebruikersnaam>.github.io/<repo-naam>/beef.json`) en zet die in `Config.kt` (stap 2 hierboven), dan opnieuw builden.

## Ontwerpkeuzes

- **fegra.be**: rechtstreeks vanaf het toestel (officiële CSV-export, geldig TLS-certificaat, geen backend nodig). Tarwe/gerst gebruiken de "gecertificeerd"-kolom; mais valt terug niet-gecert -> gecert -> "vochtig 30%" (buiten het oogstseizoen zijn alle mais-kolommen leeg -- widget toont dan "geen data").
- **Runderkarkas DU/DE**: geen directe API beschikbaar (de EU open-databron kent voor België geen DU/DE, enkel de Vlaamse Spotfire-dashboard doet dat). De pagina's eigen Spotfire JS-API bleek voor anonieme bezoekers geen waarden te leveren (enkel aantallen) tijdens het testen; de scraper gebruikt daarom dezelfde "Download data"-knop die een bezoeker ook zou gebruiken. DU/DE-prijs = gemiddelde van de vetheidsklasses (bv. DU2+DU3)/2 -- aanpasbaar in `backend/lib/computeBeefSummary.js`.
- **Nooit crashen / nooit lege widget**: elke bron faalt onafhankelijk en valt terug op de laatst gecachte waarde; de scraper publiceert nooit corrupte data (plausibiliteitscheck vóór commit).
