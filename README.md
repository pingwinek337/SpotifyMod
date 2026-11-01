# Spotify HUD (Fabric 1.21.4)

Mod kliencki pokazujący w grze, czego słuchasz na Spotify.

- **Widget „teraz gra”** w rogu ekranu (domyślnie prawy górny): okładka, tytuł, wykonawca, pasek postępu.
- **Napisy** (tekst piosenki) wiszą w świecie kilka kratek przed Tobą, zwykłą czcionką Minecrafta, zsynchronizowane z muzyką.
- **GUI ustawień** pod klawiszem **K**: połączenie konta, widget, napisy, okładka, pasek postępu,
  pozycja (4 rogi), skala, przezroczystość tła, synchronizacja napisów.
- Skróty (nieprzypisane domyślnie, ustawisz w Sterowaniu): play/pauza, następny, poprzedni
  (wymaga Spotify Premium), włącz/wyłącz napisy, włącz/wyłącz widget.

---

## Instrukcja krok po kroku (dla zupełnie początkujących)

### Krok 1 – Pobierz potrzebne rzeczy

1. **Minecraft Java 1.21.4** (np. przez oficjalny launcher).
2. **Fabric Loader** – wejdź na <https://fabricmc.net/use/installer/>, pobierz instalator,
   uruchom go, wybierz wersję **1.21.4** i kliknij **Zainstaluj**. W launcherze pojawi się profil „fabric-loader”.
3. **Fabric API** – pobierz z <https://modrinth.com/mod/fabric-api/versions>
   (wybierz wersję dla **1.21.4** i loadera **Fabric**).
4. **Ten mod** – pobierz plik `spotify-hud.jar`:
   <https://github.com/pingwinek337/SpotifyMod/releases/download/latest/spotify-hud.jar>

### Krok 2 – Wrzuć mody do folderu

1. Naciśnij `Win + R`, wpisz `%appdata%\.minecraft` i naciśnij Enter.
2. Jeśli nie ma tam folderu `mods`, utwórz go.
3. Wrzuć do `mods` **dwa pliki**: `spotify-hud.jar` oraz plik Fabric API.
4. Uruchom Minecrafta z profilu **fabric-loader**.

### Krok 3 – Utwórz „aplikację” Spotify (jednorazowo, 3 minuty)

Spotify wymaga, żeby każdy mod miał swój identyfikator. Darmowy, robi się go raz.

1. Wejdź na <https://developer.spotify.com/dashboard> i zaloguj się swoim kontem Spotify.
   Zaakceptuj regulamin, jeśli o to poprosi.
2. Kliknij **Create app**.
3. Wypełnij:
   - **App name:** `Minecraft HUD` (dowolna nazwa)
   - **App description:** `Mod do Minecrafta` (dowolny opis)
   - **Redirect URI:** wpisz **dokładnie** `http://127.0.0.1:8888/callback` i kliknij **Add**
   - **Which API/SDKs are you planning to use?** zaznacz **Web API**
4. Zaznacz zgodę na regulamin i kliknij **Save**.
5. Wejdź w **Settings** swojej aplikacji i skopiuj **Client ID** (długi ciąg liter i cyfr).

### Krok 4 – Połącz konto w grze

1. Wejdź do dowolnego świata i naciśnij **K** – otworzy się okno Spotify HUD.
2. Wklej skopiowany **Client ID** w pole tekstowe (Ctrl+V).
3. Kliknij **Połącz ze Spotify**. Otworzy się przeglądarka – kliknij **Zgadzam się / Agree**.
4. Zobaczysz zieloną stronę „Konto połączone”. Wróć do gry – u góry okna będzie napis
   **Połączono jako (Twoja nazwa)**.

### Krok 5 – Słuchaj muzyki

1. Włącz muzykę na Spotify (w telefonie, aplikacji na komputerze lub przeglądarce).
2. W prawym górnym rogu gry pojawi się widget z tym, co leci.
3. Żeby włączyć **napisy** (tekst piosenki): naciśnij **K** i kliknij przełącznik **Napisy**.
4. W tym samym oknie zmienisz pozycję widgetu, jego rozmiar i przezroczystość.

### Gdy coś nie działa

| Problem | Rozwiązanie |
|---|---|
| Po naciśnięciu K nic się nie dzieje | Zamknij czat/ekwipunek. Sprawdź klawisz w *Opcje → Sterowanie → Klawisze → Spotify HUD*. |
| Gra się nie uruchamia | Sprawdź, czy w `mods` jest też **Fabric API** i czy uruchamiasz profil Fabric 1.21.4. |
| W przeglądarce błąd „INVALID_CLIENT” lub „redirect_uri” | Redirect URI w dashboardzie musi być dokładnie `http://127.0.0.1:8888/callback` (bez spacji, z `127.0.0.1`, nie `localhost`). |
| „Port 8888 jest zajęty” | Zamknij program, który go używa, i spróbuj ponownie. |
| Widget się nie pojawia | Muzyka musi aktualnie grać na Spotify. Sprawdź przełącznik **Widget** w ustawieniach. |
| Brak napisów | Nie każdy utwór ma tekst. Status zobaczysz w oknie ustawień (po prawej stronie linii „Połączono”). |
| Napisy są przesunięte | Użyj suwaka **Sync napisów** w ustawieniach. |
| Przyciski play/następny nie działają | Wymagają konta Spotify **Premium**. |

---

## Uwagi

- Tokeny logowania zapisują się w `config/spotifyhud.json` – **nie udostępniaj tego pliku**.
- Napisy pochodzą z darmowego [LRCLIB](https://lrclib.net) (Spotify nie udostępnia tekstów w API).
- Aplikacje Spotify w trybie deweloperskim działają dla właściciela i użytkowników dodanych w dashboardzie
  (Settings → User Management).

## Budowanie ze źródeł

```bash
./gradlew build
```

Gotowy plik: `build/libs/spotify-hud-1.0.0.jar`. Każdy push na `main` buduje też plik automatycznie
(GitHub Actions) i wystawia go w zakładce Releases.

## Struktura

```
src/main/java/com/spotifyhud/
  SpotifyHudMod.java        wejście moda, skróty klawiszowe
  HudConfig.java            ustawienia (config/spotifyhud.json)
  hud/HudRenderer.java      widget + napisy
  gui/                      ekran ustawień i własne widgety
  spotify/                  logowanie (PKCE), odpytywanie API, napisy (LRCLIB)
```
