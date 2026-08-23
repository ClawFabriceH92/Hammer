# Hammer

Application Android de stress test réseau (LAN + site web), 100% locale — aucun backend, aucune télémétrie.

Voir [`CAHIER_DES_CHARGES.md`](CAHIER_DES_CHARGES.md) pour le périmètre complet (v1/v2/v3) et l'addendum de révisions (§16).

## État du projet

Implémentation v1 :

- **Garde-fous** (`core/`) : whitelist RFC1918 bloquée côté code, rate limiter token-bucket, hard cap de concurrence à 500, auto-stop sur >90% d'échec — chacun couvert par des tests unitaires dédiés (`src/test/`).
- **Moteurs de charge** (`engine/`) : HTTP GET/POST (OkHttp), TCP raw (`java.net.Socket`), profils Constante / Rampe / Burst / Max.
- **Stats & export** (`stats/`, `export/`) : compteurs live, percentiles p50/p95/p99 sur fenêtre glissante de 10 000 requêtes, export CSV/JSON vers `Documents/Hammer/`.
- **Service foreground** (`service/`) : notification persistante avec action STOP en un tap.
- **UI** (`ui/`, Compose M3) : écran unique conforme au cahier des charges §11, écran de confirmation avant GO, réglages, historique, FR/EN.

Pas encore implémenté : v2 (multi-cible, UDP/WebSocket/DNS, planification, watchdog, A/B) et v3 (Slowloris/RST flood, multi-appareils, replay) — hors périmètre v1 par design.

## Build

Projet Gradle standard (AGP 8.5.2, Kotlin 1.9.24, Compose). Le JAR du Gradle Wrapper n'est pas versionné dans ce dépôt (binaire non généré depuis cette session) : ouvrez le projet dans Android Studio (qui régénère le wrapper automatiquement), ou lancez `gradle wrapper` localement avec une installation Gradle existante avant d'utiliser `./gradlew`.

```bash
./gradlew testDebugUnitTest   # tests unitaires (garde-fous, rate limiter, etc.)
./gradlew assembleDebug       # build de l'APK debug
```

## Sécurité

- Aucun SDK de télémétrie/crash reporting.
- Cible IP restreinte aux plages privées RFC1918 (IPv4 uniquement), refusée côté code.
- Toute cible "site internet" démarre plafonnée à 20 req/s tant qu'elle n'est pas explicitement débloquée dans l'écran de confirmation.
