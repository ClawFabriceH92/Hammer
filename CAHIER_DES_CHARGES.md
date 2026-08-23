# Hammer — Cahier des charges v1.0 (périmètre complet)

**Date :** 22/08/2026
**Statut :** périmètre complet consolidé — v1 à implémenter, v2/v3 séquencées
**Auteur :** Olla (agent local)

---

## Roadmap (vue d'ensemble)

| Version | Contenu |
|---|---|
| **v1** | Cible IP + site, HTTP + TCP raw, 4 profils, garde-fous, stats live, exports, foreground service, multi-langue |
| **v2** | Multi-cible, plage subnet, UDP / WebSocket / DNS, profils Step + Poisson, A/B de runs, mode planifié, watchdog, en-têtes templates |
| **v3** | Slowloris / RST flood, **multi-appareils synchronisés** (Nearby), replay de séquence, dark mode |

> Toute option destructrice (slowloris, RST flood, burst très élevé sur une plage) reste soumise aux **mêmes garde-fous RFC1918** et à la **confirmation avant GO**. Le champ « site internet » (domaine) n'accepte que les attaques de charge **non destructrices** (GET/POST/TCP raw) — c'est un stress test, pas un outil d'intrusion.

---

## 1. Objectif

Application Android de **stress test réseau** : bombarder une cible (périphérique LAN ou site internet) pour mesurer sa capacité d'absorption, sa latence sous charge et le point exact où elle casse.

- **Aucun backend, aucune dépendance cloud** — tout tourne sur le téléphone.
- **2 modes de cible** : périphérique réseau (IP + port) **et site internet** (domaine / URL complète).
- **Protocoles** : HTTP(S) + TCP raw en v1 ; extension UDP / WebSocket / DNS en v2.
- **Évolutions** : multi-appareils (v3), planification (v2), replay (v3).

---

## 2. Target

| Option | Version | Description |
|---|---|---|
| **IP locale** | v1 | `192.168.0.20` + port séparé (box, caméra, routeur, serveur local) |
| **Site internet** | v1 | Domaine (`exemple.fr`) ou URL complète (`https://exemple.fr/api`) — port déduit (443/80) sauf override |
| **Path** | v1 | En mode site : chemin de l'URL ou paramètre séparé (`/`, `/api/health`) |
| **Multi-cible** | v2 | Round-robin entre N URLs/IPs (max 10), charge répartie équitablement |
| **Plage subnet** | v2 | `192.168.0.1` → `192.168.0.50` (scan de charge sur une plage complète) |
| **DNS** | v2 | Nom de domaine + port explicite (ex : `router.lan.local:8080`) |

> **Règle de sécurité v1** : le champ IP n'accepte **que** les plages privées RFC1918 (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`). Viser une IP publique via le champ IP est **bloqué côté code** (pas juste UI). Le champ « site internet » (domaine) reste libre — c'est le mode « stresser son propre serveur web en ligne ».

---

## 3. Protocoles

### v1
| Mode | Charge par requête |
|---|---|
| **HTTP GET** | `GET {path} HTTP/1.1` + en-têtes base (User-Agent, Accept, Host). **3 champs en-têtes custom** optionnels (ex `Authorization`, `X-API-Key`, `Content-Type`) |
| **HTTP POST** | Body fixe (max 16 Ko), Content-Type paramétrable |
| **TCP raw** | Connexion → écriture d'un paquet (taille 64 B → 64 Ko, pattern : zéro / aléatoire / texte choisi) → close |

### v2
| Mode | Charge |
|---|---|
| **UDP** | Paquets envoyés sans attendre réponse — taille + pattern + rate (mesure de débit réseau, pas de latence applicative) |
| **WebSocket** | Connexion WS maintenue + messages ping/pong en boucle — teste la persistance et le serveur WS |
| **DNS** | Queries `A` / `AAAA` / `SRV` en boucle vers un résolveur (test d'un résolveur local ou publique) |

### v3 (destructif — usage strict LAN, confirmation renforcée double-tap)
| Mode | Description |
|---|---|
| **Slowloris** | Ouverture de connexions HTTP, envoi partiel de la requête, maintien — épuise les connexions acceptées |
| **RST flood** | Open → close immédiat en boucle — mesure le rate de rétablissement |

---

## 4. Profils de charge

### v1
| Profil | Comportement |
|---|---|
| **Constante** | N req/s plafonné (token-bucket), N = 1 → 1000 |
| **Rampe** | 0 → N req/s linéairement sur X secondes (10 s → 120 s) |
| **Burst** | X req en Y ms (burst), pause Z s, répétition jusqu'à STOP ou N cycles |
| **Max** | Pas de limite de rate : push à fond les concurrents disponibles |

### v2
| Profil | Comportement |
|---|---|
| **Step** | Paliers successifs (ex : 100 → 200 → 400 → 800 req/s), pause de Y s entre paliers, auto-stop au dernier |
| **Poisson** | Arrivées aléatoires selon loi de Poisson (moyenne = N req/s) — profil réaliste de trafic web |

### v3 (destructif — LAN uniquement, double confirmation)
| Profil | Comportement |
|---|---|
| **Burst agressif** | 1000+ req en < 100 ms, pause courte — voir §3 « Slowloris / RST flood » |

---

## 5. Réglages exécutables

| Réglage | v1 | Valeur / options |
|---|---|---|
| **Concurrents** | ✅ | Slider 1 → 200 (OkHttp `maxRequestsPerHost` + `maxRequests`) |
| **Durée** | ✅ | 10 s / 30 s / 60 s / 5 min / jusqu'à STOP |
| **Rate limit** | ✅ | Actif/inactif + valeur req/s (désactivé en mode « Max ») |
| **Timeout réponse** | ✅ | 500 ms / 1 s / 5 s / 30 s |
| **Taille paquet TCP** | ✅ | 64 B → 64 Ko (log-scale slider) |
| **Pattern paquet** | ✅ | Zéro / Aléatoire / Texte |
| **En-têtes custom** | ✅ | 3 champs nom + valeur (v2 : template + variables `{{timestamp}}`, `{{rand}}`) |
| **Body POST** | ✅ | Max 16 Ko, Content-Type libre |
| **Cooldown entre bursts** | v2 | 1 s → 600 s |
| **Cycles max (Burst)** | v2 | 1 → 10 000 ou « indéfini » |
| **Mode « friendly »** | v1 | Un seul tap : 50 concurrents, 30 s, GET constante — safe par défaut |

---

## 6. Garde-fous (sécurité)

| Garde-fou | Version |
|---|---|
| **Whitelist IP** : RFC1918 uniquement (bloqué côté code, pas UI) | v1 |
| **Confirmation avant démarrage** : écran récap (cible, mode, rate, durée) + bouton « GO » | v1 |
| **Hard cap global** : 500 concurrents max, non modifiable sans debug flag | v1 |
| **Auto-stop sur erreurs** : taux d'échec > 90 % sur 10 s → stop + rapport | v1 |
| **Stop d'urgence** : dans la **notification** (sans ouvrir l'app) + bouton écran | v1 |
| **Cooldown obligatoire** entre deux runs | v1 (5 s) / v2 (réglable) |
| **Log local** : `hammer_log_{date}.log` dans `Documents/Hammer/` — **aucun envoi réseau** des logs | v1 |
| **Whitelist de plages** (multi-cible / subnet) : toutes les IP d'une plage doivent être RFC1918 | v2 |
| **Double confirmation** (tap 2×) pour les profils destructifs (slowloris, RST flood) | v3 |
| **Refus explicite** des profils destructifs en mode « site internet » (domaine) | v3 (bloqué à l'UI) |

---

## 7. Stats & export

### 7.1 Dashboard live (MAJ 500 ms) — v1
- **Req/s instantané** + sparkline 60 s
- **Total envoyé / OK / échoué**
- **Latence** p50 / p95 / p99 (HTTP) — TCP raw : temps moyen connect + close
- **Erreurs groupées** : `connection refused`, `timeout`, `reset`, `SSL error`, `4xx` / `5xx`
- **Uptime du run** + temps restant

### 7.2 Fin de run — v1
- **Rapport récapitulatif** : moyenne req/s, pic, taux d'erreur, code le plus fréquent, latences percentiles
- **Export CSV** : `timestamp ; req_id ; ok ; latence_ms ; code_erreur`
- **Export JSON** : stats agrégées
- Sauvegarde automatique des **10 derniers runs** dans `Documents/Hammer/`
- **Partage** : bouton « Partager » (file:// → app de partage Android)

### 7.3 v2
| Fonction | Description |
|---|---|
| **Comparaison A/B** | Charger 2 runs → diff côte à côte (moyenne, pics, erreurs) |
| **Historique riche** | Liste des 50 derniers runs, recherche par cible/date, re-lancer un run identique en 1 tap |
| **Capture d'état cible** | Si la cible expose un endpoint `/health` ou `/metrics`, Hammer le fetch avant/après pour enrichir le rapport |
| **Export graphique** | PNG de la sparkline 60 s + tableau de bord complet (v2 : rendu simple canvas) |

---

## 8. Multi-appareils — v3

| Élément | Description |
|---|---|
| **Coordination** | Via **Google Nearby Connections** (même transport que Roomy) — pas de backend, pas d'Internet |
| **Architecture** | 1 appareil = **master** (regroupe les stats), N-1 = **slaves** (envoient leur req/s + erreurs au master) |
| **Config partagée** | Le master définit le run (cible, mode, rate), les slaves héritent via message Nearby |
| **Débit cumulé** | Le dashboard du master affiche la **somme** des req/s de tous les appareils |
| **Départ synchro** | Master envoie « GO » → tous les slaves démarrent dans les 50 ms (toléré) |
| **Limites** | ≤ 5 appareils par session (Nearby), tous sur le même VLAN/Wi-Fi, master doit maintenir l'app en premier plan |

---

## 9. Mode planifié & watchdog — v2

### 9.1 Planifié (cron maison via WorkManager)
- **Fréquence** : horaire fixe (ex : 02:00), intervalle (ex : toutes les 6 h), one-shot (date + heure)
- **Run à exécuter** : référence à une config sauvegardée (cible + mode + rate + durée)
- **Résultat** : rapport envoyé dans `Documents/Hammer/` + notif Android de fin
- **Garde-fou** : un planifié peut **pas** lancer de profil destructif — bloqué à l'UI

### 9.2 Watchdog (surveillance continue)
- **Uniquement** : mode GET constant **1 req/s** (faible débit, surveillance)
- **Seuils** : latence moyenne > X ms sur N s → alerte, ou taux d'échec > Y % → alerte
- **Alerte** : notification Android + option de sauvegarde auto du run en cours
- **Usage typique** : surveiller la box / le routeur / un serveur 24/7 — Hammer détecte la dégradation

---

## 10. Replay de séquence — v3

- **Enregistrer** : during un run, Hammer capture la séquence (timestamp + requête) dans un fichier `.ham`
- **Rejouer** : charge un `.ham` et rejoue la même séquence de requêtes (même timing relatif)
- **Usage** : reproduire le run qui a cassé la cible hier → re-tester après un correctif
- **Taille max** : 10 000 requêtes par fichier

---

## 11. Interface (v1, 1 écran)

Style clair :
- Fond blanc cassé `#E5E7EB`, carte blanche, bordure 1 px, **aucune ombre**
- **Sélecteur segmenté cible** : `IP locale` | `Site internet`
- **Sélecteur protocole** : `HTTP` | `TCP raw` (désactivé en mode site)
- **Sélecteur profil** : `Constante` | `Rampe` | `Burst` | `Max`
- **Zone stats live** centrée (chiffres en gros, sparkline)
- **Bouton** `▶ START` vert / `⏹ STOP` rouge
- **Bouton** `📋 Export` actif à la fin du run
- **Bouton** `⚙ Reglages` (rate, concurrents, timeout, cooldown)
- **Bouton** `📚 Historique` (10 derniers runs, v1) / `📊 A/B` (v2) / `👥 Multi-tel` (v3)

**Notification foreground** :
- Ligne 1 : `Hammer — {cible} — {req/s} live`
- Action : **⏹ STOP** (un tap, pas besoin d'ouvrir l'app)
- Badge de progression : `12/60 s`

**Écran always-on (v1)** :
- Toggle « Écran allumé pendant le run » — si allumé, `FLAG_KEEP_SCREEN_ON` activé, désactivé à la fin
- Demande d'exemption batterie : `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (une fois, si refus → warning)

---

## 12. Stack technique

| Élément | Choix |
|---|---|
| Langage | Kotlin + Compose (M3, thème clair) |
| HTTP | **OkHttp 4.x** — `Dispatcher(maxRequests, maxRequestsPerHost)` + `ConnectionPool` |
| TCP raw | `java.net.Socket` via `ExecutorService` + semaphores |
| UDP | `java.net.DatagramSocket` (v2) |
| WebSocket | OkHttp `WebSocketListener` (v2) |
| DNS | `InetAddress.getByName()` + `DnsText` (v2) |
| Rate limiting | Token-bucket maison dans `ScheduledExecutorService` |
| Stats | `ConcurrentHashMap` de compteurs, file circulaire 10 000 latences pour p50/p95/p99 |
| Persistance | `.csv` / `.json` dans `Documents/Hammer/` (MediaStore, `RELATIVE_PATH`), `SharedPreferences` pour la dernière config |
| Service | `ForegroundService` (type `dataSync`), notification persistent + action STOP |
| Cible TLS | `HttpsURLConnection` natif du système, trust store Android standard |
| Scheduler | `WorkManager` (v2 : mode planifié) |
| Multi-tel | **Google Play Services Nearby Connections** (v3, même dépendance que Roomy) |

**Permissions manifeste (v1)** :
```
INTERNET
ACCESS_NETWORK_STATE
FOREGROUND_SERVICE
FOREGROUND_SERVICE_DATA_SYNC
POST_NOTIFICATIONS
WAKE_LOCK (maintien CPU pendant le run)
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
ACCESS_FINE_LOCATION (v3, Nearby Connections)
BLUETOOTH_CONNECT (v3, Nearby)
```

---

## 13. Contraintes & limites

- **Batterie / thermique** : un run « Max » de 5 min échauffe le téléphone ; Android peut throttler le réseau. Prévoir pause.
- **Wi-Fi obligatoire** : le mode data mobile n'est pas géré (et est peu adapté au stress LAN).
- **Écran verrouillé** : le foreground service maintient le run, mais Doze peut ralentir le rate après ~10 min. Recommandation : screen always-on activé + exemption batterie demandée.
- **Cible qui se protège** (firewall, rate limit serveur) : les erreurs 429 / reset sont comptées comme des données du rapport, pas des erreurs Hammer.
- **Aucune mesure de la cible** : Hammer mesure ce qu'il *voit* (round-trip), pas le CPU/RAM du périphérique.
- **Multi-appareils (v3)** : Nearby Connections plafonne à ~5 endpoints par session ; au-delà il faut segmenter le test.
- **Planification** : WorkManager a une granularité minimale d'environ 60 min pour les tâches répétées courtes — à vérifier sur API 34+.

---

## 14. Critères d'acceptation

### v1
1. Stresser `exemple.fr` (site) en GET constant 100 req/s pendant 60 s → rapport généré, export CSV ok.
2. Stresser `192.168.0.x:80` (box) en TCP raw 200 concurrents → refuseds comptés, auto-stop si > 90 % échec.
3. Stop depuis la **notification** en 1 tap sans ouvrir l'app.
4. Run en écran verrouillé : le run continue **≥ 5 min** sans crash.
5. Zéro envoi de données hors de la cible choisie (log réseau vérifiable).
6. Viser `8.8.8.8` via le champ IP → **refusé**. Viser `exemple.com` → accepté (mode site).
7. Mode « friendly » : 50 concurrents, 30 s, GET — s'exécute sans erreur sur une cible réelle.

### v2 (à la livraison)
8. Multi-cible : 3 cibles (2 LAN + 1 site), round-robin, stats agrégées correctes.
9. Plage subnet `192.168.0.10`–`192.168.0.20` : les 11 cibles reçoivent la charge.
10. UDP : les paquets arrivent (vérif via capture tcpdump sur la cible).
11. Mode planifié : run à 02:00 exécuté, rapport présent, notif reçue.
12. Watchdog : latence simulée > 500 ms sur 5 s → alerte notification.
13. A/B : 2 runs sur la même cible, diff affiché.

### v3 (à la livraison)
14. Slowloris : la cible épuise ses connexions (testé sur serveur local avec `ulimit -n` bas).
15. Multi-appareils : 2 téléphones, master affine le run, le débit cumulé est ~2×.
16. Replay : run `.ham` enregistré puis rejoué → même séquence de temps (± 100 ms).

---

## 15. Hors périmètre (out of scope, tout périmètre)

- Test de pénétration / exploitation (Hammer est un outil de charge, pas d'attaque)
- Visée d'infrastructures publiques tierces (le mode « site internet » est prévu pour ses **propres** serveurs)
- Génération automatique de rapports PDF / HTML (v4 candidate)
- Intégration CI / webhook de résultat (v4 candidate)
