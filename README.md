# Spotify HUD (Fabric 1.21.4)

Mod kliencki pokazujący w grze, czego słuchasz na Spotify.

- **Widget „teraz gra”** w rogu ekranu (domyślnie prawy górny): okładka, tytuł, wykonawca, pasek postępu.
- **Napisy** (tekst piosenki) wyświetlane przy dole ekranu zwykłą czcionką Minecrafta, z synchronizacją
  do muzyki. Włączane w ustawieniach lub skrótem klawiszowym.
- **Ładne GUI ustawień** (domyślnie klawisz `K`): połączenie konta, widget wł./wył., napisy, okładka,
  pasek postępu, pozycja (4 rogi), skala, przezroczystość tła, synchronizacja napisów.
- Skróty (do ustawienia w Sterowaniu): play/pauza, następny, poprzedni (wymaga Spotify Premium).

## Wymagania

- Minecraft **1.21.4**, Fabric Loader ≥ 0.16.10, Fabric API, Java 21.

## Budowanie

```bash
./gradlew build
```

Gotowy plik: `build/libs/spotify-hud-1.0.0.jar` → wrzuć do `.minecraft/mods`.

> Jeśli Gradle zgłosi, że nie ma wersji Fabric API, zmień `fabric_version` w `gradle.properties`
> na aktualną dla 1.21.4 (https://modrinth.com/mod/fabric-api/versions).

## Połączenie z kontem Spotify (jednorazowo)

Spotify wymaga własnej „aplikacji” do logowania:

1. Wejdź na <https://developer.spotify.com/dashboard> → **Create app**.
2. Jako **Redirect URI** wpisz dokładnie: `http://127.0.0.1:8888/callback`
3. Zaznacz **Web API** i zapisz. Skopiuj **Client ID**.
4. W grze naciśnij `K`, wklej Client ID i kliknij **Połącz ze Spotify**. Zalogujesz się w przeglądarce.

Uwagi:

- Aplikacje w trybie deweloperskim działają tylko dla użytkowników dodanych w dashboardzie
  (Settings → User Management).
- Tokeny zapisują się w `config/spotifyhud.json` – nie udostępniaj tego pliku.
- Napisy pochodzą z darmowego API [LRCLIB](https://lrclib.net) (Spotify nie udostępnia tekstów w API),
  więc nie każdy utwór je ma.

## Struktura

```
src/main/java/com/spotifyhud/
  SpotifyHudMod.java        wejście moda, skróty klawiszowe
  HudConfig.java            ustawienia (config/spotifyhud.json)
  hud/HudRenderer.java      widget + napisy
  gui/                      ekran ustawień i własne widgety
  spotify/                  logowanie (PKCE), odpytywanie API, napisy (LRCLIB)
```
