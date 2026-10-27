# AGENTS.md — joutio-bff

> **BFF unique** : façade REST + WebSocket des services Joutio (headless), personnalisée pour
> `joutio-web`. Architecture : Axon (query/command bus distribués) + Kafka + STOMP.
> Ce fichier est **normatif** pour la surface BFF ↔ web : toute route ajoutée ou modifiée doit
> s'y conformer et y être documentée.
> Patterns backend : [`../../best-practices/.backend/folder-structure.md`](../../best-practices/.backend/folder-structure.md).

---

## 1. Rôle

- Expose **la seule surface HTTP/WS** applicative (`/api`, `/ws`).
- Compose les lectures via le **query bus** Axon (`QueryGateway`) et les écritures via le
  **command bus** (`CommandGateway`).
- Consomme le **flux d'événements Kafka** (`@ProcessingGroup`) et **fan-out** les notifications
  vers un socket client unique (`/topic/games/{id}`, `/topic/rooms/{id}`,
  `/topic/notifications/{userId}`, `/topic/presence/{userId}`). **Multi-réplique** : les groupes de
  fan-out (`game-notification`, `room-notification`, `matchmaking-notification`,
  `presence-notification`, `follow-presence-notification`, `achievement-notification`,
  `notification-ws`) sont
  **instance-scopés** (`joutio.axon.instance-scoped-groups`) — chaque pod consomme tous les
  événements avec son propre token (suffixé par `HOSTNAME`, token initial à la queue du flux) —
  sinon les clients STOMP d'une réplique ne recevraient pas les événements consommés par une autre.
- Porte la **présence joueur** (sessions STOMP client → `joutio-profile`) : chaque instance BFF a
  un identifiant stable (`application:host`), purge au démarrage ses sessions antérieures, renouvelle
  en batch le bail de ses sessions locales (`RenewPresenceSessionsCommand`, 10 s), déconnecte ses
  sessions locales à l'arrêt (`@PreDestroy`) et retente les commandes de présence tant que le
  routage distribué n'est pas prêt.

**Package** : `com.joutio.bff` · **Port** : `8092` (local) / `8080` (prod) · **DB** : `joutio_bff`

Le `joutio-mobile` est **partiellement** branché sur cette surface : auth native via
`joutio-identity`, `GET /api/me`, `GET /api/home` et l'édition du profil
(`PUT /api/profiles/{id}/pseudonym|bio|country|language`) ; les autres écrans restent sur
l'ancienne surface et sont migrés lot par lot.

---

## 2. Conventions de surface

- **Surface hybride** :
  - ressources explicites + query params typés pour les collections
    (`GET /api/topics?category=&sort=&page=&size=`) ;
  - endpoints d'**agrégation nommés** pour les écrans multi-services
    (`GET /api/me`, `GET /api/home`, `GET /api/topics/{id}/overview`).
- **Aucun `POST /{collection}/search` exposé.** Les use cases de recherche restent dans les
  services headless et sont réservés aux futures surfaces d'administration. Le BFF ne compose
  jamais un compteur, une facette, un historique ou une liste de page à partir d'une recherche
  générique : il utilise une requête dédiée (batch / count / facettes).
- **DTOs de façade uniquement** : `*View` (lecture) et `*Request` (écriture). Jamais de read
  model interne exposé (`Challenge`, `Game`, `Room`, `Profile`, `Topic`…), jamais de `Map`,
  `Object` ou cast non typé. **Exception** : les noms de sujet sont exposés en valeur typée
  `Map<Language,String> names` (clé = code ISO 639-1), le client choisit sa langue avec repli FR
  (comme `translations` des questions) ; jamais de `String name` mono-langue.
- **Notifications web** : DTOs d'infrastructure BFF (`GameNotification`, `RoomNotification`,
  `MatchmakingNotification`, `NotificationView` pour l'inbox personnelle) enveloppés dans
  `EventEnvelopeResponse` (ossature Jackson du web). Les événements reçus du bus sont des
  `EventEnvelope` SDK au payload typé (`eventType`) ;
  un `*-domain` ne porte **ni notification ni annotation framework**. `RoundStartedNotification`
  expose `questionText`/`answers` (langue source) **et** `translations` (toutes les langues du
  snapshot, clé = code ISO 639-1) : le client choisit sa langue, avec repli sur la source.
  `GameCreatedNotification` expose `questionImageUrls` (images des questions, ordre des rounds)
  pour le préchargement client dès la création de la partie.
- **Pagination** : `?page=&size=` → `PageResponse<T> { content, page, size, totalElements,
  totalPages, first, last }`. **Tris par enum documenté** (phrase d'URL/valeurs fermées), jamais
  de nom de propriété arbitraire.
- **Relations** : `PUT /api/{resource}/{id}/follow` (idempotent) puis
  `DELETE /api/{resource}/{id}/follow` (`204`).
- **Transitions d'état** : `POST /api/{resource}/{id}/{action}`. `DELETE` est réservé à la
  disparition effective de la ressource.
- **Composition côté BFF** : enrichissements (profils, Elo, présences, compteurs, noms,
  avatars) systématiquement résolus ici, jamais côté client. Les lectures batch passent par des
  requêtes dédiées (`GetProfilesByIdsQuery`, `GetProgressionsByIdsQuery`,
  `GetPresencesByIdsQuery`…).
- **Progression** : `ProgressionView.badges` liste les badges **acquis**, chacun avec sa
  **famille** et son **palier** (`BadgeFamily` / `BadgeTier` — Fer → Diamant) ; la **collection
  complète**
  (paliers verrouillés + progression « 7/10 ») est exposée par
  `GET /api/profiles/{userId}/badges`, composée depuis le catalogue `Badge` et les compteurs du
  read model (`gamesPlayed`, `wins`, `badges`, `xpByTopic`), **sans requête supplémentaire**.
  `ProgressionView` ne porte plus de niveau, de titre de compte ni d'XP globale : uniquement les
  badges et l'Elo. `TopicOverviewView.myProgress` porte la maîtrise du sujet (XP, parties,
  `tier`) ; les titres affichés sont thématiques (`equippedTitle` + `GET …/titles`). Le palier
  `tier` (`FER`…`DIAMANT`) **est** la rareté : il n'y a plus de `rank` séparé. `MeView.rewards`
  expose les récompenses persistées
  (`MICAH_AVATAR`, …) ; l'avatar de base est gratuit, Micah exige `MICAH_AVATAR` ou Premium,
  Lorelei/Notionists restent Premium. `GET /api/me/rewards` expose le catalogue complet
  (débloquées avec date/source, verrouillées avec leur condition).
  > Pin `joutio.profile-domain.version` : **release majeure du lot rareté** (XP par thème, Elo,
  > paliers `MasteryTier`, `titles` en `tier`, suppression de `TitleRank`). Le pin doit rester
  > aligné sur la release qui porte ces contrats, sinon les vues ne compileront pas.
- **Titres thématiques** : le serveur ne compose **jamais** le libellé (« Or · Histoire ») —
  il expose le palier (`MasteryTier`, à traduire côté client) et le sujet (`names`), le client
  combine. Un titre est débloqué par un sujet avec **au moins une partie humaine terminée**
  (abandon inclus ; un entraînement bot, à 0 XP, ne débloque rien) — le palier d'entrée
  **Fer compris même à 0 XP**. La vue `/titles` expose **tous les paliers atteints** d'un
  sujet (Fer → palier courant) et pas seulement ce dernier : le joueur peut équiper un palier
  dépassé depuis la fiche sujet, et ce titre doit rester résolvable côté client. Le **titre
  porté** (`Profile.equippedTitle`) est validé à l'écriture : le BFF vérifie que le sujet a une
  partie humaine terminée et que le palier est réellement atteint **avant** d'envoyer la commande
  (`command-preparation`).
- **Caches de lecture (Caffeine)** : `ProfileLookup` (profil, 30 s, purgé par `ProfileUpdateService`
  après chaque écriture), `ProgressionLookup` (progression, 15 s — lue sur chaque `/api/me`/fiche/
  enfilage), `RewardLookup` (récompenses, 30 s, purgé sur `RewardGrantedEvent`) et les
  **compteurs de follows** (`FollowLookup.userCounts`, 30 s, purgés après un follow/unfollow dans
  `ProfileController`). Objectif : amortir les requêtes distribuées sous
  polling/temps réel (charge validée 1000 connexions WS).
  **Règle multi-réplicas** : l'invalidation locale (`ProfileController`/`ProfileUpdateService`) n'est
  qu'un **fast-path** — `CacheInvalidationHandler` (groupe **instance-scopé** `cache-invalidation`,
  chaque pod consomme tous les événements) purge le cache de **chaque** réplique sur les événements
  social (`UserFollowed/UnfollowedEvent` : les deux côtés) et profile (événements de champ).
  Toute donnée mutable mise en cache doit suivre ce modèle (événement → purge sur toutes les
  réplicas) ou avoir un TTL volontairement borné. Après une purge, la **fenêtre no-cache** (3 s)
  des lookups évite de mémoriser une projection encore en retard.
- **Actor** : le `userId` provient du JWT (`SecurityHelper`) ; jamais dans le body.
- **Session périmée / compte suspendu** : un JWT encore valide dont le `user_id` n'existe plus
  dans identity (base purgée, compte supprimé) est rejeté en `401` par
  `CurrentUserExistsFilter` sur `/api/**` ; un compte suspendu est rejeté en `403`
  `urn:joutio:authorization:accountSuspended` (avec `suspendedUntil`). Le client purge sa
  session et repasse par le login. Statut résolu via `FindUserQuery` (`Optional<User>`, cache court
  30 s + fenêtre no-cache de 3 s après invalidation ; identité injoignable ⇒ fail-open). Le
  `Optional.empty()` permet de distinguer un compte disparu d'une panne distante : avec
  `GetUserAccountStatusQuery`, l'exception Axon distante perdait parfois la cause métier et
  l'utilisateur recevait un 404 de profil au lieu du 401 `unknownUser`. Les
  réponses 401/403 portent les **en-têtes CORS** de la configuration du service (le filtre
  court-circuite avant le `CorsFilter` servlet, même ordre par défaut) : sans eux le navigateur
  masque la réponse et le client web ne peut pas purger sa session.
- **Entrées** : `@Valid`, enums stricts, `ProblemDetail` en erreur. Aucun parsing permissif,
  aucune valeur par défaut inventée (`level=1`, `OFFLINE` fabriqué…).
- **Composition asynchrone** : combiner les `QueryGateway.query()` (pas de `.join()` bloquant
  sur le thread HTTP).
- Contrôleurs **minces** : aucune composition ni règle métier dans un `*Controller` ; tout vit
  dans un service d'`application/`.

---

## 3. Surface cible (normative)

### Compte / coquille

| Endpoint | Réponse | Notes |
|---|---|---|
| `GET /api/me` | `MeView` | Profil (+ `language` fr/en, + `equippedTitle` = clé du titre porté, + `interests` = codes `TopicCategory` ordonnés, premier = univers principal, max 3) + progression + stats de duel + `followingCount` + `followersCount` + `onboardingCompleted` (flag serveur, ou première partie humaine déjà jouée) + `rewards` (codes des récompenses persistées, ex. `MICAH_AVATAR`) + `premium` (`ROLE_ADMIN` par défaut ou `ROLE_PREMIUM`) |
| `GET /api/me/rewards` | `RewardsView` | Catalogue complet des récompenses : `code`, `unlocked`, `unlockedAt`, `source`, `condition` (`badgeCode`, `badgeLabel`, `target`). Les déblocages viennent de `player_reward`, les verrouillées de `RewardRules.catalog()` ; réponse privée au joueur courant. |
| `POST /api/me/onboarding/complete` | `MeView` | Fin explicite ou skip de l'onboarding ; idempotent, renvoie le flag `onboardingCompleted=true` sans attendre la projection profile |
| `PUT /api/me/interests` | `204` | Remplace les centres d'intérêt du joueur courant (`{ interests: ["HISTORY","SCIENCE"] }`) : 0 à 3 codes `TopicCategory`, ordre conservé (premier = univers principal), doublons refusés (`400`), liste vide efface. Envoie `UpdateProfileInterestsCommand` et invalide le cache profil. |
| `GET /api/me/onboarding/recommendations?interests=HISTORY,SCIENCE` | `List<TopicCardView>` | Recommandations pour l'onboarding avant persistance : 1 à 3 codes, jusqu'à 3 sujets **publiés et jouables** (≥ 7 questions approuvées), un par univers en priorité puis complément, ordre des intérêts conservé (premier = thème proposé). |
| `GET /api/suggestions?q=&limit=` | `List<SuggestionView>` | Palette ⌘K : sujets + joueurs (`type`, `id`, libellé, visuel) |
| `GET /api/clock` | `ServerTimeView` | `serverTime`, `epochMillis` |
| `POST /api/telemetry/events` | `202` | Événement produit allowlisté (`onboarding_started/completed/skipped`, `play_mode_opened`, `duel_start_requested`, `matchmaking_expired`, `result_viewed`, `paywall_viewed` ; `properties` bornées) → compteur Prometheus `joutio_product_events_total` (tags `event/mode/first/outcome/bot/auto`), logs debug ; jamais de SaaS. Rate-limit 120/min/joueur |
| `POST /api/telemetry/client-errors` | `202` | Erreur navigateur (`type`, `message`, `stack`, `route`, `buildId`, `source`) → compteur `joutio_client_errors_total` + log `client_error` (Loki). Route normalisée, stack tronquée, rate-limit 20/min/joueur |

### Accueil

| Endpoint | Réponse | Notes |
|---|---|---|
| `GET /api/home` | `HomeView` | `followedTopics` (récents) + `trendingTopics` (les plus joués **en ce moment** : parties des **24 h**, tous joueurs confondus, via `game`) + `newTopics` (récemment publiés ou mis à jour — tri theme `RECENT`, limités à 10) + `recommendedTopics` (« Pour toi », à partir des `Profile.interests` persistés ; sujets publiés/jouables uniquement, un par univers puis complément, suivis exclus, liste vide sans intérêt ou en cas d'échec de recommandation). Le Top 10 est **complété par le tri theme `MOST_PLAYED`** quand moins de 10 sujets ont été joués sur 24 h : les récents gardent la tête, le relais all-time complète sans doublon (liste bornée à 10) |

### Sujets (theme + social + leaderboard + profile)

| Endpoint | Réponse | Notes |
|---|---|---|
| `GET /api/topics?q=&category=&followed=&mine=&sort=&page=&size=` | `PageResponse<TopicCardView>` | `sort` ∈ `POPULAR\|MOST_PLAYED\|ALPHA\|RECENT` (défaut `POPULAR` ; `MOST_PLAYED` = compteur communautaire de parties du thème) ; `followed=true` filtre côté serveur ; `mine=true` = sujets créés par le joueur courant (brouillons + publiés, tri `updatedAt desc`), exclusif de `followed`/`q`/`category`/`sort` (400 sinon). `TopicCardView.status` expose le statut |
| `GET /api/topics/facets?q=&followed=` | `TopicFacetsView` | `total` + `categories[{ category, label, count }]` |
| `GET /api/topic-categories` | `List<TopicCategoryView>` | `category`, `label` |
| `GET /api/topics/{topicId}/overview` | `TopicOverviewView` | `topic`, `titles` (titres thématiques par langue puis palier — onglet « Titres » de la fiche sujet), `followersCount`, `myRank`, `myProgress`, `canManage` (créateur). `myProgress` porte la **maîtrise du sujet** : `games` (parties humaines terminées — verrou du titre Fer), `tier` (`MasteryTier`), `tierProgressPercent`, `tierRemainingXp`, `nextTier`, `completedQuestions`, `totalQuestions`, `completionPercent` — plus de niveau/titre de compte. |
| `PUT /api/topics/{topicId}/follow` | `204` | Idempotent |
| `DELETE /api/topics/{topicId}/follow` | `204` | |
| `GET /api/topics/{topicId}/leaderboard?period=&month=&scope=&page=&size=` | `TopicLeaderboardView` | `entries` paginées + `me` ; `period` ∈ `ALL_TIME\|MONTHLY`, `month` (`YYYY-MM`, mensuel uniquement, défaut = mois courant), `scope` ∈ `WORLD\|FOLLOWING\|COUNTRY` |

### Auteur (sujets + questions)

Surface d'écriture **propriétaire uniquement** : le créateur du sujet gère son brouillon, ses
questions et la publication. Les commandes du sujet portent la vérification dans
`TopicAggregate` (`requestedBy == creatorId`) ; les questions sont vérifiées côté BFF (l'agrégat
question ne connaît pas le créateur du sujet) et répondent `403` `PERMISSION` sinon.

| Endpoint | Réponse | Notes |
|---|---|---|
| `POST /api/topics` | `201 + Location` | `{ names { fr (obligatoire), en? } ≤255 chacun, description ≤500, category, emoji?, color?, imageUrl? }` → DRAFT |
| `PUT /api/topics/{topicId}/name` | `204` | `{ language, name ≤255 }` (nom d'une langue) |
| `PUT /api/topics/{topicId}/description\|category\|emoji\|color\|image-url\|banner-url` | `204` | un champ par route (description/image/bannière/emoji/color `null` efface) |
| `POST /api/topics/{topicId}/publish` | `200` | garde ≥ 7 questions approuvées |
| `GET /api/topics/{topicId}/questions?page=&size=` | `PageResponse<QuestionEditorView>` | tous statuts, contenus FR/EN + statut + difficulté |
| `POST /api/topics/{topicId}/questions` | `201 + Location` | contenus localisés `[{ language, text, answers[A-D] }]` + `correctAnswer` + `imageUrl?` → PENDING |
| `POST /api/questions/{questionId}/translations` | `200` | ajout d'une langue absente (contenu complet) |
| `PUT /api/questions/{questionId}/text\|answers\|correct-answer\|image-url` | `204` | `language` dans le body pour text/answers |
| `POST /api/questions/{questionId}/approve` | `200` | |
| `POST /api/questions/{questionId}/reject` | `200` | `{ reason ≤500 }` optionnel |

### Signalements de questions (joueur / créateur)

| Endpoint | Réponse | Notes |
|---|---|---|
| `POST /api/questions/{questionId}/reports` | `201` (créé) / `200` (déjà ouvert) | `{ gameId, reasons[], comment?, language? }` ; partie `FINISHED`, participant et question réellement jouée exigés ; snapshot de la version jouée |
| `GET /api/questions/{questionId}/reports` | `List<OwnerQuestionReportView>` | propriétaire du sujet uniquement, **sans identité** du signalant |
| `POST /api/questions/{questionId}/reports/{reportId}/resolve` | `200` | `{ resolution: RESOLVED\|DISMISSED, note? }` |
| `POST /api/questions/{questionId}/reports/resolve` | `200` | clôture en masse des signalements ouverts |

`GET /api/topics/{topicId}/questions` enrichit chaque question de `openReportCount`,
`totalReportCount`, `lastReportedAt`, `suspended`, `archived` et des métadonnées de modération.

### Administration des questions (console d'exploitation)

Surface `/api/admin/**` (`ROLE_ADMIN`, `@EnableMethodSecurity`), consommée par
`joutio-web-console`. Le catalogue couvre la création, l'édition, l'approbation/rejet, la
suspension/réactivation, l'archivage/restauration et la file des signalements.

| Endpoint | Réponse | Notes |
|---|---|---|
| `GET /api/admin/questions?q=&topicId=&status=&difficulty=&moderation=&reportedOnly=&sort=&page=&size=` | `PageResponse<AdminQuestionView>` | catalogue complet, compteurs inclus |
| `GET /api/admin/questions/{questionId}` | `AdminQuestionDetailView` | question + sujet + signalements (pseudonyme du signalant) |
| `GET /api/admin/question-topics` | `List<AdminTopicOptionView>` | options du filtre / création |
| `GET /api/admin/question-reports?status=&topicId=&suspended=&sort=&page=&size=` | `PageResponse<AdminQuestionSummaryView>` | file groupée par question (`OPEN` par défaut) |
| `GET /api/admin/question-reports/summary` | `AdminQuestionReportSummaryView` | `openReports`, `reportedQuestions`, `suspendedQuestions`, `archivedQuestions` |
| `POST /api/admin/topics/{topicId}/questions` | `201 + Location` | création admin (`PENDING`) |
| `PUT /api/admin/questions/{questionId}/text\|answers\|correct-answer\|image-url` | `204` | édition admin de n'importe quelle question |
| `POST /api/admin/questions/{questionId}/translations` | `200` | ajout d'une langue absente |
| `POST /api/admin/questions/{questionId}/approve\|reject` | `200` | modération éditoriale |
| `POST /api/admin/questions/{questionId}/suspend` | `200` | `{ note? }` |
| `POST /api/admin/questions/{questionId}/reinstate` | `200` | `{ resolveOpenReports?, note? }` |
| `POST /api/admin/questions/{questionId}/archive` | `200` | soft delete, `{ resolveOpenReports?, note? }` |
| `POST /api/admin/questions/{questionId}/restore` | `200` | `{ note? }` |
| `POST /api/admin/question-reports/{reportId}/resolve` | `200` | résolution unitaire |
| `POST /api/admin/questions/{questionId}/reports/resolve` | `200` | résolution en masse |

### Catalogue des joueurs, signalements et modération des comptes

Dépôt joueur : `POST /api/profiles/{userId}/reports` (`{ reasons[], comment?, gameId? }`) —
`201` créé / `200` déjà ouvert, cible existante, auto-signalement et compte système refusés ;
si `gameId` est fourni, partie `FINISHED`, participant et adversaire humain exigés.

Admin (`ROLE_ADMIN`, `/api/admin/**`) :

| Endpoint | Réponse | Notes |
|---|---|---|
| `GET /api/admin/users?q=&status=&reportedOnly=&page=&size=` | `PageResponse<AdminUserCatalogueView>` | catalogue paginé de tous les joueurs (compte + profil + compteurs de signalements), tri `createdAt desc` ; `q` = pseudonyme (profile), email (avec `@`) ou identifiant ; `status` ∈ `ACTIVE\|SUSPENDED` (suspension **effective**) ; `reportedOnly=true` = signalement ouvert |
| `GET /api/admin/user-reports?status=&suspended=&sort=&page=&size=` | `PageResponse<AdminReportedUserSummaryView>` | file groupée par joueur (`OPEN` par défaut) |
| `GET /api/admin/user-reports/summary` | `AdminUserModerationSummaryView` | `openReports`, `reportedUsers`, `suspendedUsers` |
| `GET /api/admin/users/search?q=&limit=` | `List<AdminUserSearchResultView>` | recherche bornée (pseudo/email/ID) |
| `GET /api/admin/users/{userId}` | `AdminUserDetailView` | compte + profil + signalements |
| `POST /api/admin/users/{userId}/suspend` | `AdminUserModerationView` | `{ reason, note?, duration?, permanent, resolveOpenReports? }` |
| `POST /api/admin/users/{userId}/reinstate` | `AdminUserModerationView` | `{ note? }` |
| `POST /api/admin/user-reports/{reportId}/resolve` | `200` | résolution unitaire |
| `POST /api/admin/users/{userId}/reports/resolve` | `200` | résolution en masse |

Le catalogue s'appuie sur `UserQuery.UserSearchQuery` (read model identity, pagination exacte) ;
le contenu de `SearchResponse` est re-matérialisé côté BFF (le query bus distribué le transporte
en `LinkedHashMap`) avant enrichissement. Les filtres admin `status` et `reported` sont interprétés
par l'adaptateur de lecture d'identity
(suspension **effective** — échéance dépassée = actif — et signalements ouverts). Le pseudonyme
est résolu via `ProfileQuery.GetProfileSuggestionsQuery`, puis filtré par `userId IN (…)` ;
les compteurs de signalements viennent de `UserReportQuery.GetUserReportCountsQuery`.
**Ordre de déploiement** : `joutio-identity` (support des filtres admin) avant le BFF — une
identity antérieure rejette ces filtres comme champs inconnus.

Le statut de compte vit dans **identity** ; le BFF expose `suspended` sur toutes les vues joueur
(`PlayerProfileView`, `PlayerCardView`, `UserRefView`, `SuggestionView`, `TopicLeaderboardView`,
`NotificationView.actorSuspended`) et refuse les JWT des comptes suspendus en
`403 urn:joutio:authorization:accountSuspended` (filtre `CurrentUserExistsFilter`, cache 30 s +
invalidation multi-répliques, fail-open si identity est injoignable). La création de défi vers
un adversaire suspendu est refusée (`409`). Les payloads temps réel de l'arène/WS ne portent pas
encore le badge en v1.

L'upload d'image de la console réutilise `POST /api/media` (pipeline MinIO existant, quota de
l'admin), puis `PUT .../image-url`.


### Médias (upload d'images)

Stockage objet **MinIO** (bucket `joutio-media`, app GitOps `apps/minio`, Ingress public
`media.joutio.com`) : le BFF est l'**unique voie d'écriture** (multipart), la lecture est
publique (`GetObject` anonyme, clés non énumérables). `POST /api/media` valide et **normalise**
l'image côté serveur (JPEG/PNG uniquement — SVG/GIF/WebP refusés —, ≤ 5 Mo, redimensionnement
1600 px, EXIF retiré), la déduplique par sha256, applique un **quota par joueur**
(`joutio.media.max-assets-per-user` / `max-bytes-per-user`), la dépose et journalise sa référence
dans `media_asset` (migration `V3__media_assets.sql`). L'URL publique retournée est ensuite
attachée aux entités via les endpoints existants (`PUT .../image-url`, `.../banner-url`,
`/api/profiles/{id}/banner`). Erreurs : `urn:joutio:bff:media:*` — `413` taille, `429` quota,
`503` stockage indisponible, `403` suppression par un autre joueur. Le seed des thèmes référence
les images mirorées du workspace (`product/wikipedia/`, voir `scripts/seed-images.py`).

| Endpoint | Réponse | Notes |
|---|---|---|
| `POST /api/media` | `201 + Location` | multipart `file` (JPEG/PNG ≤ 5 Mo) → `MediaView { id, url, contentType, sizeBytes, width, height }` ; déduplication par contenu, acteur = JWT |
| `DELETE /api/media/{mediaId}` | `204` | propriétaire de l'image uniquement ; l'objet n'est supprimé que si plus aucune référence ne le partage |

#### Administration média (console d'exploitation)

Surface **admin** `/api/admin/media` (`ROLE_ADMIN`, `@EnableMethodSecurity` via
`AdminSecurityConfiguration`), consommée **en direct** par `joutio-web-console`
(cross-origin `VITE_BFF_URL`, CORS SDK) : listing du bucket (uploads `uploads/` + seed
`wikipedia/`), statistiques, usage par joueur (quota), orphelins S3 ↔ `media_asset` et
suppression/purge. Les suppressions vérifient les **références métier** (nouvelles queries
`TopicQuery.CountImageReferencesQuery` et `ProfileQuery.CountImageReferencesQuery` — cover/bannière
de sujet, illustration de question, bannière de profil) : objet référencé ⇒ `409` sauf
`force=true` ; vérification indisponible ⇒ `503` (fail-closed). Pas d'audit persistant (logs).

| Endpoint | Réponse | Notes |
|---|---|---|
| `GET /api/admin/media/objects?origin=&q=&cursor=&size=` | `AdminMediaObjectsView` | pagination par curseur S3 (`nextCursor`), enrichi lignes `media_asset` + `referenceCount` (`null` si vérification indisponible) |
| `GET /api/admin/media/stats` | `AdminMediaStatsView` | volumes par origine, catalogue (count/bytes/owners), orphelins (`truncated` si scan borné) |
| `GET /api/admin/media/orphans` | `AdminMediaOrphansView` | objets `uploads/` sans ligne + lignes sans objet |
| `GET /api/admin/media/usage?page=&size=` | `PageResponse<AdminMediaUsageView>` | usage par joueur, octets décroissants |
| `DELETE /api/admin/media/objects?key=&force=` | `AdminMediaDeleteResultView` | supprime l'objet S3 + toutes les lignes de la clé ; `409` si référencé sans `force` |
| `DELETE /api/admin/media/assets/{mediaId}` | `AdminMediaDeleteResultView` | supprime une ligne fantôme (sans objet S3) |
| `POST /api/admin/media/purge-orphans` | `AdminMediaPurgeView` | purge les orphelins non référencés (`skippedReferenced`) |

### Joueurs / personnes (profile + social + leaderboard + game)

| Endpoint | Réponse | Notes |
|---|---|---|
| `GET /api/profiles/{userId}` | `PlayerProfileView` | Profil + progression + stats + counts + présence + `follow` + `isMe` + `equippedTitle` (clé du titre porté, `null` si aucun) + `bot`. Le compte système bot (`JoutioConstants.SYSTEM_USER_ID`) reçoit une identité **minimale** (`bot=true`, avatar/nom, progression/stats à zéro, aucun pays/présence/social) : la fiche web se réduit à l'avatar et au nom |
| `GET /api/profiles/{userId}/badges` | `BadgeCollectionView` | **Collection** : tout le catalogue groupé par famille — **13 familles, une seule échelle (métrique) par famille**, **7 paliers** (Fer → Diamant) : `VOLUME` = duels joués, **`VICTORY`** = duels gagnés, `MASTERY`, `EXPLOIT` = exploits ponctuels, `SERIES`, `ASSIDUITY`, `SOCIAL`, `CHALLENGES`, `VISION`, `PRECISION`, `SPEED`, `EXPLORATION`, `COLLECTION` ; chaque badge avec `code`, `label`, `tier`, `metric`, `target`, `earned` et `progress` (compteur courant **plafonné** à `target` : « 7/10 »). Les compteurs viennent du read model de progression (`gamesPlayed`, `wins`, `max(xpByTopic)`, `badges`, fait ponctuel) — aucune requête supplémentaire. **Assiduité** : la progression affichée utilise la **série courante effective** (0 après un jour manqué), pas le record historique ; les paliers déjà acquis restent acquis. |
| `GET /api/profiles/{userId}/activity?from=&to=` | `ActivityViewResponse` | Calendrier de série : `currentStreak` (**effective à la date de la requête**, retombe à 0 après un jour manqué), `longestStreak`, `lastActiveDate`, `totalActiveDays`, `days[]` (`date`, `games`). Fenêtre 365 jours par défaut, explicite ≤ 366 jours. Les entraînements bot ne comptent pas. |
| `GET /api/profiles/{userId}/titles` | `TitlesView` | Titres thématiques **débloqués** (**tous les paliers atteints** de chaque sujet avec **au moins une partie humaine terminée**, abandon inclus ; entraînement bot exclu — palier d'entrée **Fer compris même à 0 XP**) + `equippedKey` : `key` (`topicId:TIER`), `tier`, `tierProgressPercent`, `tierRemainingXp`, `nextTier`, `topic` (`TopicRefView`). Un titre porté sur un palier inférieur au palier courant reste donc exposé et résolvable. Le libellé est composé par le client (palier traduit + `topic.names`) — aucun texte figé côté serveur |
| `PUT /api/profiles/{userId}/title` | `204` | Titre **porté** : `{ titleKey }` (`topicId:TIER`) ou `null` pour n'en afficher aucun. Propriétaire uniquement (invariant d'agrégat, 403) ; titre **réellement débloqué** exigé — la progression est lue avant l'envoi et un **sujet sans partie humaine terminée** ou un palier insuffisant répond `400` (`urn:joutio:bff:profile:titleNotEarned`), une clé mal formée `400` (`…:invalidTitle`) |
| `GET /api/profiles/{userId}/following?q=&sort=&page=&size=` | `PageResponse<PlayerCardView>` | `sort` ∈ `RECENT\|ELO\|ALPHA` |
| `GET /api/profiles/{userId}/followers?q=&sort=&page=&size=` | `PageResponse<PlayerCardView>` | idem |
| `PUT /api/profiles/{userId}/follow` | `204` | Idempotent |
| `DELETE /api/profiles/{userId}/follow` | `204` | |
| `GET /api/profiles/{userId}/games?topicId=&opponentId=&opponentType=&outcome=&sort=&page=&size=` | `PageResponse<GameHistoryItemView>` | Historique enrichi (adversaire, sujet, résultat, XP). Filtres optionnels : `topicId`, `opponentId`, `opponentType` ∈ `HUMAN\|BOT` (isole les entraînements), `outcome` ∈ `ALL\|WIN\|LOSS\|DRAW\|IN_PROGRESS\|CANCELLED` (**relatif au joueur consulté** : `WIN`/`LOSS` se lisent sur le vainqueur). `sort` ∈ `RECENT\|OLDEST\|SCORE` (défaut `RECENT` ; `SCORE` = meilleur score du joueur consulté). Filtres et tri sont appliqués **côté service game** — la pagination reste exacte. Fiche sujet / « Tes duels contre X » passent le scope par `topicId` / `opponentId` |
| `GET /api/profiles/{userId}/topics` | `List<TopicStatView>` | Répartition des **parties humaines** par sujet (donut « Sujets joués ») : `names`, `emoji`, `color`, `imageUrl`, `games`, `xp` ; tri par parties décroissantes, sujets supprimés ignorés |
| `GET /api/profiles/{userId}/head-to-head?against=` | `HeadToHeadView` | V/N/D des duels communs **du point de vue de `userId`** ; la fiche d'un autre joueur appelle `userId = viewer` et `against = joueur consulté` |
| `PUT /api/profiles/{userId}/pseudonym` | `204` | `{ pseudonym }` |
| `PUT /api/profiles/{userId}/bio` | `204` | `{ bio }` (`null` efface) |
| `PUT /api/profiles/{userId}/country` | `204` | `{ country }` (`null` efface) |
| `PUT /api/profiles/{userId}/avatar-options` | `204` | `{ avatarOptions }` (`null` efface). `style:"base"` (icône + fond) est gratuit ; `micah` exige la récompense `MICAH_AVATAR` ou Premium (`403 urn:joutio:authorization:micahLocked`), `lorelei\|notionists` exigent Premium (`403 urn:joutio:authorization:premiumRequired`). |
| `PUT /api/profiles/{userId}/banner` | `204` | `{ bannerUrl }` (`null` efface) — bannière de profil (URL image http(s) ≤1024) |
| `PUT /api/profiles/{userId}/language` | `204` | `{ language }` ∈ `fr\|en` (enum strict) |
| `GET /api/presence/{userId}` | `PresenceView` | `404` si le joueur ne s'est jamais connecté (l'absence vaut hors ligne) ; poussé aussi en WS |

### Appariement public (matchmaking)

| Endpoint | Réponse | Notes |
|---|---|---|
| `POST /api/matchmaking/tickets` | `201 + Location` | Body `{ topicId }` — lance la recherche ; l'appariement crée une **salle** nominative (jamais la partie). Le serveur ne bascule **jamais** seul vers un bot : à l'échéance **10 s** le ticket expire ; le client applique la préférence locale `autoTrainOnTimeout` (entraînement automatique par défaut, sinon popup relance/entraînement). Le BFF prépare la **photo de couverture linguistique** du sujet (`GetTopicLanguageCoverageQuery`) et la porte dans la commande : la saga compare localement |
| `GET /api/matchmaking/tickets/{ticketId}` | `MatchmakingTicketView` | Statut `SEARCHING\|COMPLETED\|CANCELLED\|EXPIRED\|FAILED`, `roomId`, `opponentId`, `vsBot` |
| `POST /api/matchmaking/tickets/{ticketId}/cancel` | `200` | Annule la recherche |
| `GET /api/matchmaking/tickets/{ticketId}/notifications` | `List<EventEnvelopeResponse>` (payload `MatchmakingNotification`) | Même contrat que le push WS |

### Défis nominatifs (matchmaking)

Intention **asynchrone** (TTL 1 h, **10 s pour une revanche** portant un `gameId`) distincte de la
salle temps réel : l'acceptation crée la salle (saga matchmaking) qui porte présence, préparation
(statuts) et création de la partie.

| Endpoint | Réponse | Notes |
|---|---|---|
| `POST /api/challenges` | `201 + Location` | Body `{ topicId, opponentId, gameId? }` — l'adversaire doit exister (`404` sinon) ; `gameId` = **partie terminée d'origine** d'une revanche, validée (le challenger a joué, duel humain/humain, `opponentId` cohérent) |
| `GET /api/challenges/mine` | `List<ChallengeView>` | Défis `PENDING` où le joueur est challenger ou opposant |
| `GET /api/challenges/{challengeId}` | `ChallengeView` | Sujet, challenger/opponent, statut (`PENDING\|ACCEPTED\|DECLINED\|CANCELLED\|EXPIRED`), `roomId` dès l'acceptation, `gameId` de la partie d'origine (revanche) |
| `POST /api/challenges/{challengeId}/accept` | `200` | Invité uniquement ; la salle est créée par la saga (`roomId`) |
| `POST /api/challenges/{challengeId}/decline` | `200` | Invité uniquement |
| `POST /api/challenges/{challengeId}/cancel` | `200` | Lanceur uniquement (défi sans réponse) |

**Garde anti-spam** (`DuelCreationGuard`, avant l'envoi de la commande) :
- un défi `PENDING` ne peut pas être dupliqué entre les **mêmes deux joueurs** sur le **même sujet**, dans aucun sens (`409 urn:joutio:bff:challenge:alreadyPending`) ;
- le nombre de défis `PENDING` **créés** par le joueur (challenger) est plafonné par `joutio.duel.limits.pending-challenges` (défaut 5) → `429 urn:joutio:bff:challenge:quotaExceeded`. Les défis reçus ne comptent pas.
- La revanche est soumise aux mêmes règles.

### Salles (matchmaking)

| Endpoint | Réponse | Notes |
|---|---|---|
| `POST /api/rooms` | `201 + Location` | Body `{ topicId }` — **salle partagée** par lien `/join/{roomId}` ; le défi nominatif passe par `POST /api/challenges` |
| `POST /api/rooms/bot` | `201 + Location` | Body `{ topicId, difficulty? }` — **salle bot** : le bot rejoint la salle automatiquement, l'humain y apparaît ensuite. **Exemptée de l'unicité par sujet** : un salon privé ouvert ne bloque pas l'entraînement ; quota bot propre |
| `GET /api/rooms/mine` | `List<RoomView>` | Salles `WAITING_PLAYERS` du joueur (initiateur **ou** invité) — filet de reprise |
| `GET /api/rooms/{roomId}` | `RoomView` | Sujet, `status` (`WAITING_PLAYERS\|FETCHING_PLAYERS\|FETCHING_PLAYED_QUESTIONS\|FETCHING_QUESTIONS\|CREATING_GAME\|GAME_CREATED\|COMPLETED\|CANCELLED\|EXPIRED\|FAILED`), `vsBot`, `canCancel` (viewer = initiateur), adversaire, présences, `gameId` |
| `POST /api/rooms/{roomId}/join` | `200` | **Apparition** (idempotente) : présence et, pour le second joueur, enregistrement du participant ; nominatif : invité uniquement (403 sinon). Émise par le client quand l'utilisateur est dans la salle ; le BFF prépare le **snapshot `RoomPlayer`** (profil + progression, caches Caffeine) et le porte dans `JoinRoomCommand` — la salle ne résout jamais les joueurs (préparation ensuite non bloquante : profils portés par l'apparition, historique/tirage par requêtes async) |
| `POST /api/rooms/{roomId}/leave` | `200` | Sortie **non destructive** (idempotente) tant que la préparation n'est pas engagée ; refusée (400) dès que les deux joueurs sont présents — seul `cancel` ferme la salle |
| `POST /api/rooms/{roomId}/cancel` | `200` | Annulation par l'initiateur, possible tant que la salle n'est pas terminale |
| `POST /api/rooms/{roomId}/wizz` | `200` | **Relance** de l'adversaire humain **absent** (salle nominative, émetteur présent) — pur temps réel, aucune préparation déclenchée ; cadence **10 s par joueur** (le refus métier remonte le `Problem` mappé 4xx) |
| `GET /api/rooms/{roomId}/notifications` | `List<EventEnvelopeResponse>` (payload `RoomNotification`) | Même contrat que le push WS |

> Un état terminal (`COMPLETED`/`CANCELLED`/`EXPIRED`/`FAILED`) est conservé le temps de la rétention (2 min)
> puis purgé : les commandes tardives répondent 404 (pré-vérification BFF) ou un `Problem` métier,
> jamais un 500.

**Garde de création** (`DuelCreationGuard`) : un joueur ne peut avoir qu'un salon **privé/partagé**
`WAITING_PLAYERS` **créé** par sujet → `409 urn:joutio:bff:room:alreadyOpenOnTopic`. Le nombre de
salons privés/partagés `WAITING_PLAYERS` créés est plafonné par `joutio.duel.limits.open-rooms`
(défaut 5) → `429 urn:joutio:bff:room:quotaExceeded`. Les salons **bot** sont comptés séparément
(même limite) et sont **exemptés de l'unicité par sujet** : un salon privé ouvert ne bloque pas
`POST /api/rooms/bot`, et un salon bot ne bloque pas `POST /api/rooms`. Les salons où le joueur
n'est qu'invité ne comptent pas. `DuelLimitPolicy` est le point d'extension unique du premium
(limites rehaussées ou illimitées quand le plan existera) ; les défauts sont surchargeables par
`JOUTIO_DUEL_MAX_PENDING_CHALLENGES` / `JOUTIO_DUEL_MAX_OPEN_ROOMS`.

### Notifications personnelles (joutio-notification)

| Endpoint | Réponse | Notes |
|---|---|---|
| `GET /api/notifications?unreadOnly=&page=&size=` | `PageResponse<NotificationView>` | Inbox du joueur courant |
| `GET /api/notifications/unread-count` | `{ count }` | Badge de la cloche |
| `POST /api/notifications/{notificationId}/read` | `200` | Propriétaire uniquement (404/403 sinon). **Sans pré-lecture du read model** : la commande part directement (la projection peut être en retard juste après le toast) ; agrégat absent ⇒ 404, propriétaire invalide ⇒ 403 |
| `POST /api/notifications/{notificationId}/unread` | `200` | Repasse en non lue — propriétaire uniquement (404/403 sinon), même règle sans pré-lecture |
| `DELETE /api/notifications/{notificationId}` | `204` | Hard delete — propriétaire uniquement (404/403 sinon), même règle sans pré-lecture |
| `DELETE /api/notifications` | `204` | Vide l'inbox du joueur courant (hard delete, fan-out de commandes unitaires idempotentes) |
| `POST /api/notifications/read-all` | `200` | Fan-out de commandes unitaires idempotentes |
| `GET /api/notification-preferences` | `List<{ category, enabled }>` | `category` ∈ `FOLLOW\|ROOM\|QUESTION` (défaut activé) |
| `PUT /api/notification-preferences/{category}` | `204` | `{ enabled }` |

Types ingérés (Kafka → inbox) : `FOLLOW`, `CHALLENGE_RECEIVED`, `CHALLENGE_DECLINED`,
`ROOM_ACCEPTED`, **`WIZZ`** (relance de salle — une seule entrée par salle, remplacée à chaque
relance, expirée quand la cible rejoint ou à la fin de la salle), **`QUESTION_REPORTED`**
(signalement d'une question d'un sujet du destinataire, catégorie `QUESTION`, une entrée par
signalement `reportId`) et **`ACCOUNT_SUSPENDED`** (suspension du compte, catégorie `ACCOUNT`
obligatoire, une entrée par `suspensionId`, lisible après réactivation). Le BFF pousse chaque
`NotificationCreatedEvent` sur `/topic/notifications/{userId}` et en Web Push.

### Web Push (navigateur)

Abonnements par navigateur portés par le BFF (`push_subscription`, migration `V2__push_subscriptions.sql`) :
l'envoi est branché sur le processing group `notification-push` (**groupe global**, jamais
instance-scopé : une seule notification OS par événement, même avec plusieurs répliques — le
fan-out STOMP de l'inbox vit dans `notification-ws`, instance-scopé), hors thread du processor.
Payload structuré (`type`, `actorPseudonym`, `sourceId`, `path`…) ;
le Service Worker web compose le texte et route le clic. Routes de clic notables :
`CHALLENGE_RECEIVED` → `/social?tab=challenges&type=received&challenge={sourceId}` (carte du
défi), `ROOM_ACCEPTED` → `/game/{gameId}` ou `/rooms/{sourceId}`, `WIZZ` → `/rooms/{sourceId}`,
`FOLLOW` → `/players/{actorId}`. **Urgence `high`** pour les notifications
time-sensitive (`CHALLENGE_RECEIVED`, `ROOM_ACCEPTED`, `WIZZ`) : en `normal`, FCM
diffère la livraison quand l'appareil est verrouillé/en Doze ; le header `Topic` collapse les
invitations d'un même défi et les **relances** d'une même salle. Configuration `joutio.push.vapid.*`
(clé publique dans le ConfigMap, privée dans le secret sealed) : clés absentes ⇒ push désactivé.

| Endpoint | Réponse | Notes |
|---|---|---|
| `GET /api/push/vapid-public-key` | `{ publicKey }` | `404` si Web Push non configuré ; clé = `applicationServerKey` du `PushManager.subscribe` |
| `PUT /api/push/subscriptions` | `204` | idempotent, body `{ endpoint, keys: { p256dh, auth } }`, actor = JWT (upsert/rebind) |
| `DELETE /api/push/subscriptions?endpoint=…` | `204` | désabonnement au logout (propriétaire uniquement) |

### Duel (game)

> La **création** d'une partie (bot compris) passe exclusivement par la salle
> (`POST /api/rooms` ou `POST /api/rooms/bot`) : la salle est l'unique point d'entrée de
> préparation.

| Endpoint | Réponse | Notes |
|---|---|---|
| `GET /api/games?active=true` | `List<ActiveGameView>` | Parties en cours du joueur (`IN_PROGRESS`), plus récentes d'abord ; collection **vide** si aucune (jamais de 404). Seul `active=true` est exposé (l'historique vit sous `/api/profiles/{id}/games`) |
| `POST /api/games/{gameId}/answer` | `200` | Body `{ choice }` |
| `POST /api/games/{gameId}/abandon` | `200` | Forfait en cours (`ForfeitGameCommand`) — l'adversaire gagne |
| `POST /api/games/{gameId}/cancel` | `200` | Annulation avant démarrage |
| `GET /api/games/{gameId}/result` | `GameResultView` | Résultat après `GAME_ENDED` : scores, détail (`basePoints`, `speedBonus`, `correctAnswers`…), `reward` XP **du sujet** (`null` tant que la projection n'est pas disponible ; `{ xp: 0, victoryBonus: 0 }` pour un **entraînement**), Elo **à l'instant de la partie** (snapshot `game` + variation), `topicMastery` (XP du sujet, `tier`, progression) et **enrichissements autosuffisants** : `topic`, `opponent`, `opponentId`, `botDifficulty`. **Privé** : le viewer doit être l'un des deux joueurs, sinon `403 urn:joutio:bff:game:accessDenied` |
| `GET /api/games/{gameId}/notifications` | `List<EventEnvelopeResponse>` (payload `GameNotification`) | Même contrat que le push WS. **Privé** : la participation est vérifiée **sur le flux d'événements lui-même** (`GameCreatedEvent`), jamais sur la projection `game` (asynchrone) — l'historique est donc disponible dès l'accusé `ROOM_GAME_CREATED`, sans 404 transitoire. Flux vide ⇒ `404 urn:joutio:game:notFound` ; tiers ⇒ `403 urn:joutio:bff:game:accessDenied`. Aucun historique de partie pour un tiers |

---

## 4. WebSocket (STOMP)

Endpoint `/ws` (SockJS) ; broker `/topic`. Une connexion par client, JWT en `CONNECT`.
Heartbeats STOMP activés côté serveur (`heartbeat-outgoing`/`heartbeat-incoming`, 10 s par
défaut, fournis par le SDK) : un client silencieux est fermé par le broker en ~30 s, ce qui
déclenche la déconnexion de session côté présence.

| Destination | Payload |
|---|---|
| `/topic/games/{gameId}` | `EventEnvelopeResponse` (payload `GameNotification`) (`GAME_CREATED`, `GAME_STARTED`, `ROUND_STARTED`, `QUESTION_REVEALED`, `PLAYER_ANSWERED`, `ROUND_CLOSED`, `GAME_FORFEITED`, `GAME_ENDED`, `GAME_CANCELLED`) |
| `/topic/rooms/{roomId}` | `EventEnvelopeResponse` (payload `RoomNotification`) (`ROOM_CREATED`, `ROOM_ENTERED`, `ROOM_ALL_PRESENT`, `ROOM_PLAYERS_LOADED`, `ROOM_PLAYED_QUESTIONS_LOADED`, `ROOM_QUESTIONS_LOADED`, `ROOM_GAME_CREATED`, `ROOM_LEFT`, `ROOM_WIZZED`, `ROOM_COMPLETED`, `ROOM_CANCELLED`, `ROOM_EXPIRED`, `ROOM_FAILED`) |
| `/topic/matchmaking/tickets/{ticketId}` | `EventEnvelopeResponse` (payload `MatchmakingNotification`) (`SEARCHING`, `MATCHED`, `CANCELLED`, `EXPIRED`, `FAILED`) |
| `/topic/notifications/{userId}` | `EventEnvelopeResponse` (payload `NotificationView`) — inbox personnelle (invitations de défi, follows) ; suppression poussée sous `NOTIFICATION_DELETED` (payload `{ notificationId }`) |
| `/topic/presence/{userId}` | `PresenceView` — **exception assumée** : événements de session non séquencés, pas d'enveloppe |
| `/topic/follow-presence/{userId}` | `FollowPresenceView` (`actorId`, `pseudonym`, `avatarOptions`, `at`) — **fan-out éphémère** : quand un joueur passe en ligne, chaque **abonné** reçoit « X est en ligne » ; jamais persisté (pas d'inbox), poussé par le groupe `follow-presence-notification` |
| `/topic/achievements/{userId}` | `AchievementView` (`kind` ∈ `BADGE\|TITLE`, badge `code`/`label`/`family`/`tier` + `metric`/`target` (objectif atteint), `topicId`+`tier`, `at`) — **fan-out éphémère des déblocages** (badge gagné, titre thématique débloqué) : le client fête l'acquisition où qu'il soit (popup + son), hors écran immersif où elle est reportée ; jamais persisté (pas d'inbox), groupe `achievement-notification` **instance-scopé** |

`EventEnvelopeResponse` : `aggregateId`, `sequenceNumber`, `timestamp`, `eventType` (type web),
`payload` (DTO de notification). L'historique REST (`GET .../notifications`) et le push WS
partagent exactement le même contrat (dédup par `sequenceNumber` côté client). Les DTOs de
notification (`GameNotification`, `RoomNotification`, `MatchmakingNotification`, `NotificationView`)
vivent dans `infrastructure/out/messaging/response/` ou `in/api/response/` ; **aucune notification
ni annotation Jackson dans un `*-domain`**. Le transport des événements du query bus est
l'`EventEnvelope` du SDK (payload typé via `eventType`, sans annotation), mappé ici vers
`EventEnvelopeResponse`.

---

## 5. Non périmètre

- Aucune logique métier d'agrégat (elle reste dans les services headless).
- Pas d'event store propre (seul le token store Axon, fourni par le SDK).
- Pas de back-office global : la surface d'**auteur** (`/api/topics` écriture, `/api/questions`)
  est ouverte à tout joueur pour **ses propres** sujets ; les use cases de recherche génériques
  restent réservés aux futures surfaces d'administration. **Exceptions actées** : la surface
  `/api/admin/media` (console média) et la surface `/api/admin` des questions (console
  d'exploitation : catalogue, modération, signalements) — voir § Médias et § Administration des
  questions.
- Erreurs de façade : les `BaseProblem` levés hors handlers Axon (gardes propriétaire, chaînage
  asynchrone) sont mappés par `BffProblemExceptionHandler` (RFC 7807, `PERMISSION` → 403).

---

## 6. Tests

- Un test `WebMvcTest` par contrôleur (gateways mockés) : chemin, paramètres, mapping DTO.
- Un test unitaire par service de composition (`application/`).
- `mvn -pl joutio-bff-infrastructure test` avant tout commit.
