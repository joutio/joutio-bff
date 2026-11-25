# AGENTS.md — joutio-bff

**Seule surface REST + STOMP** des clients Joutio. Le BFF compose les lectures par le bus de
requêtes, prépare et envoie les commandes, et pousse les événements aux clients connectés. Il
porte aussi les médias, le push, la télémétrie, l'administration et les liens universels. Package
`com.joutio.bff`, base `joutio_bff`, port local 8092 (8080 en prod).

Lire : `../../AGENTS.md`, puis **[`docs/api/`](../../docs/api/README.md)**, le contrat normatif
(conventions, sécurité, caches, WebSocket, une fiche par domaine). Toute route ajoutée ou modifiée
y est documentée dans le même commit.

## Structure

- `application/` (à plat) : services de composition par écran ou domaine (`*ViewService`,
  `MeService`, `HomeService`…) ; lectures en cache `*Lookup` (`ProfileLookup`, `ProgressionLookup`,
  `RewardLookup`, `FollowLookup`, `IdentityLookup`) ; règles `*Policy`/`*Guard`
  (`PremiumAccessPolicy`, `DuelLimitPolicy`, `DuelCreationGuard`, `TopicCreationLimitPolicy`,
  `AvatarEntitlementPolicy`, `TopicAccessService`) ; médias, push, partage, administration.
- `application/live` : vues vivantes. `LiveView` (destination, règle d'accès locale, requête, version, mapper),
  ses deux formes `VersionedLiveView` (`LiveUpdate` sur `/topic/live/<vue>/{clé}`) et `AggregateStreamLiveView`
  (flux d'agrégat), le catalogue `LiveViews` (résolution d'une destination, lecture `GET /api/live/<vue>/{clé}`).
  Vues : `GameRewardLiveView` (`game-reward`, clé `{gameId}:{userId}`), `GameStreamLiveView` (`/topic/games/{id}`,
  flux d'agrégat).
- `infrastructure/in/api` : contrôleurs minces, `*Request`, `*View`.
- `infrastructure/in/websocket` : `PresenceChannelListener` (canaux de présence → commandes de
  salle et de ticket), sessions de présence joueur et contrôle d’abonnement au canal personnel des salles ;
  `LiveViewChannelConfiguration` (accès aux vues vivantes avant le broker, suivi des abonnements après lui),
  `LiveViewSubscriptions` (pont : un `subscriptionQuery` par destination et par pod, compté par références, valeur
  courante au nouvel abonné, rouvert et comblé après la perte des fournisseurs) et `LiveViewSender` (diffusion, ou
  envoi à un seul abonnement).
- `infrastructure/in/share` : pages `go.joutio.com`.
- `infrastructure/out/messaging` : publishers temps réel, mappers événement → réponse BFF. Les handlers d’agrégat reçoivent le payload typé et les métadonnées utiles via les résolveurs Axon.
- `infrastructure/out/{media,push,persistence}` : MinIO, Web Push, Expo, tables propres.
- `infrastructure/config` : chaînes de sécurité (`ShareSecurityConfiguration` @Order 1,
  `PublicApiSecurityConfiguration` @Order 2, `AdminSecurityConfiguration`), quotas, push, médias.
- Aucune logique d'agrégat ; pas d'event store propre (seulement le token store Axon).

## Processing groups

| Groupe | Portée |
|---|---|
| `room-events`, `matchmaking-events`, `presence-events`, `follow-presence-events`, `achievement-events`, `notification-ws`, `cache-invalidation` | **instance-scopés** : chaque pod reçoit tout, en direct, par un seul consumer Kafka sans token (SDK) ; handlers non bloquants |
| `notification-push` | global : un seul envoi Web Push / Expo par événement, hors thread du processor |
| `account-deletion-bff` | global : purge des données du BFF à la suppression d'un compte |

## Données propres

Tables :
- `media_asset` : déclaration de droits, sha256, quota ;
- `push_subscription` ;
- `expo_push_subscription` et `expo_push_delivery` ;
- `content_notice`.

Schéma dans `V1__init_bff.sql`.

## Configuration

| Préfixe | Rôle |
|---|---|
| `joutio.duel.limits.*`, `joutio.creation.limits.*` | quotas gratuit / Premium ([parties](../../docs/api/parties.md#salles), [sujets](../../docs/api/sujets.md#atelier-écriture)) |
| `joutio.media.*` | bucket, quotas par joueur, `require-rights-declaration` |
| `joutio.push.vapid.*`, `JOUTIO_EXPO_PUSH_*` | Web Push (sans clés = désactivé), push Expo |
| `joutio.share.*` | liens universels (image par défaut, App Store, Android) |
| `joutio.account.terms-version`, `.birth-date-required-since` | CGU en vigueur, date de naissance exigée |
| `joutio.room-presence.disconnect-grace` | grâce de déconnexion d'un salon (10 s) |
| `joutio.axon.instance-scoped-groups` | groupes de fan-out |

## Commandes

```bash
mvn -pl joutio-bff-infrastructure test     # avant tout commit
JOUTIO_EXPO_TEST_DB_URL=jdbc:postgresql://… mvn -pl joutio-bff-infrastructure test -Dtest=ExpoPushJdbcStoreTest
```

## Vues vivantes

- **Une vue vivante = un `LiveView`** (bean Spring). Ni publisher, ni handler d'événements, ni cache pour la servir :
  le pont et `LiveViews` font le reste. Contrat : [api](../../docs/api/README.md#vues-vivantes).
- `canSubscribe` est évaluée sur le thread entrant STOMP : règle locale, **sans I/O**.
- La valeur du service porte une version monotone (séquence de l'agrégat) ; le pont ne relaie qu'une version
  supérieure à la dernière relayée.
- Les abonnements sont suivis **après** leur traitement par le broker (`ExecutorChannelInterceptor`), pas par les
  `Session*Event` : ces événements partent avant que le broker n'ait inscrit l'abonné, qui manquerait alors la valeur
  courante.

## Pièges

- Pins des `*-domain` : les releases des fournisseurs passent avant le bump ici
  ([contrats](../../docs/backend/contrats-et-build.md#versions-et-releases)) ; une valeur d'enum inconnue arrive en
  `null`.
- La recherche paginée d'identity revient du bus en `LinkedHashMap` : la re-matérialiser.
- Les réponses `401`/`403` de `CurrentUserExistsFilter` doivent porter les en-têtes CORS.
- Une donnée mutable mise en cache est purgée sur **toutes** les répliques par
  `CacheInvalidationHandler`, sinon le TTL doit rester court.
- Écarts connus avec les règles : `make backlog REPO=joutio-bff TYPE=dette`.
