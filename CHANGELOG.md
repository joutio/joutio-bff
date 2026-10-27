## 1.0.0 (2026-10-27)

### Features

* initial Joutio BFF service ([cbef7ee](https://github.com/joutio/joutio-bff/commit/cbef7ee98cd5791a03791b5a1d6f82a6ca303a0a))

## [13.1.1](https://github.com/joutio/joutio-bff/compare/v13.1.0...v13.1.1) (2026-10-26)

### Bug Fixes

* **bff:** sert l'historique de partie depuis l'event store ([1fcf94f](https://github.com/joutio/joutio-bff/commit/1fcf94fbc7d612917e3de7a14599b3830c58fe2f))

## [13.1.0](https://github.com/joutio/joutio-bff/compare/v13.0.0...v13.1.0) (2026-10-26)

### Features

* **bff:** expose les centres d'intérêt et les recommandations personnalisées ([e800908](https://github.com/joutio/joutio-bff/commit/e80090893b7c46676c57ebfcc8197183a20f00f3))

## [13.0.0](https://github.com/joutio/joutio-bff/compare/v12.1.0...v13.0.0) (2026-10-25)

### ⚠ BREAKING CHANGES

* **bff:** les champs grade/nextGrade/rank sont remplacés par
tier/nextTier ; les clés de titres sont topicId:TIER.

### Features

* **bff:** expose les paliers de rareté dans les vues ([9597de6](https://github.com/joutio/joutio-bff/commit/9597de68670a3bbcc4615bbd44a939fab6c802d2))

## [12.1.0](https://github.com/joutio/joutio-bff/compare/v12.0.0...v12.1.0) (2026-10-23)

### Features

* **bff:** expose le catalogue des récompenses du joueur ([36aeaf1](https://github.com/joutio/joutio-bff/commit/36aeaf1743f6de9c62a6ccea90e0467c71f3795a))

## [12.0.0](https://github.com/joutio/joutio-bff/compare/v11.46.0...v12.0.0) (2026-10-23)

### ⚠ BREAKING CHANGES

* **bff:** expose les récompenses Micah et passe la progression joueur à l'Elo

### Features

* **bff:** expose les récompenses Micah et passe la progression joueur à l'Elo ([e267552](https://github.com/joutio/joutio-bff/commit/e267552e669e8cfa1fc00e9c6c2ef4978ada61df))

## [11.46.0](https://github.com/joutio/joutio-bff/compare/v11.45.0...v11.46.0) (2026-10-23)

### Features

* **avatar:** réserve les styles DiceBear au premium ([1f4f62f](https://github.com/joutio/joutio-bff/commit/1f4f62f042ba5654040d7e76a4b4d470024cf670))

## [11.45.0](https://github.com/joutio/joutio-bff/compare/v11.44.3...v11.45.0) (2026-10-23)

### Features

* **bff:** aligne les seuils de progression sur joutio-profile 4.12.0 ([ead0acf](https://github.com/joutio/joutio-bff/commit/ead0acf12b34e112c438361e6ec125b3b79889ae))

## [11.44.3](https://github.com/joutio/joutio-bff/compare/v11.44.2...v11.44.3) (2026-10-23)

### Bug Fixes

* **session:** rejette les JWT des comptes supprimés après reset ([bbbca10](https://github.com/joutio/joutio-bff/commit/bbbca107ebefc5e31fb7db4cb9bfd5fd9450ae7d))

## [11.44.2](https://github.com/joutio/joutio-bff/compare/v11.44.1...v11.44.2) (2026-10-23)

### Bug Fixes

* **deps:** aligne le pin profile sur la release 4.11.1 ([d88b4b8](https://github.com/joutio/joutio-bff/commit/d88b4b8f02a6709924acedd694e2733e45d4a39e))

## [11.44.1](https://github.com/joutio/joutio-bff/compare/v11.44.0...v11.44.1) (2026-10-23)

### Bug Fixes

* **onboarding:** renvoie le flag complété sans attendre la projection ([73ba919](https://github.com/joutio/joutio-bff/commit/73ba9192cd9b345c0541e674bc8102fc88a40275))

## [11.44.0](https://github.com/joutio/joutio-bff/compare/v11.43.2...v11.44.0) (2026-10-23)

### Features

* **onboarding:** expose le flag serveur et la fin d'onboarding ([16a1d65](https://github.com/joutio/joutio-bff/commit/16a1d65d1826ece7c46b1540be25eeddaabbfa20))

## [11.43.2](https://github.com/joutio/joutio-bff/compare/v11.43.1...v11.43.2) (2026-10-22)

### Bug Fixes

* **game:** expose l'Elo relatif à la partie et le snapshot de salle ([a3b9b3e](https://github.com/joutio/joutio-bff/commit/a3b9b3ed2e7ac849b8a25a50aac7ebe4f58952a0))

## [11.43.1](https://github.com/joutio/joutio-bff/compare/v11.43.0...v11.43.1) (2026-10-22)

### Bug Fixes

* **game:** masque les comptes supprimés et bloque revanche/signalement ([2d9e6bf](https://github.com/joutio/joutio-bff/commit/2d9e6bfd7634ab0c014eef3dfaffdc4c083a2612))

## [11.43.0](https://github.com/joutio/joutio-bff/compare/v11.42.0...v11.43.0) (2026-10-22)

### Features

* **account:** valide la suppression par OTP seul, sans phrase à recopier ([7d29b78](https://github.com/joutio/joutio-bff/commit/7d29b783120ff643172ea926234b33dcd14f6f83))

## [11.42.0](https://github.com/joutio/joutio-bff/compare/v11.41.0...v11.42.0) (2026-10-22)

### Features

* **account:** expose la suppression de compte et purge médias/push ([9700cfd](https://github.com/joutio/joutio-bff/commit/9700cfd875542cceb1490f5996590c9ae1bae619))

## [11.41.0](https://github.com/joutio/joutio-bff/compare/v11.40.1...v11.41.0) (2026-10-22)

### Features

* **bff:** autorise les événements d'onboarding ([1b005fe](https://github.com/joutio/joutio-bff/commit/1b005feeba4d243f9116f194a1b30ba13e3999ee))

## [11.40.1](https://github.com/joutio/joutio-bff/compare/v11.40.0...v11.40.1) (2026-10-22)

### Bug Fixes

* **bff:** expose le mode auto du repli bot en télémétrie ([ecc2c67](https://github.com/joutio/joutio-bff/commit/ecc2c67537df65d9e1af1e4b0b6ec17caa8eabbf))

## [11.40.0](https://github.com/joutio/joutio-bff/compare/v11.39.0...v11.40.0) (2026-10-22)

### Features

* **bff:** calendrier de série et progression assiduité courante ([55b9ddf](https://github.com/joutio/joutio-bff/commit/55b9ddfed21a11fce3053eac1e3d3971c4e8d945))

## [11.39.0](https://github.com/joutio/joutio-bff/compare/v11.38.1...v11.39.0) (2026-10-22)

### Features

* **bff:** télémétrie produit et erreurs client interne ([604ea57](https://github.com/joutio/joutio-bff/commit/604ea57542886dc471c3efd183789e8c0734fdcf))

## [11.38.1](https://github.com/joutio/joutio-bff/compare/v11.38.0...v11.38.1) (2026-10-21)

### Bug Fixes

* **bff:** expose l'Elo dans le classement ([24219b6](https://github.com/joutio/joutio-bff/commit/24219b66901fbe4a6c70f6d010a2692c74abc526))

## [11.38.0](https://github.com/joutio/joutio-bff/compare/v11.37.0...v11.38.0) (2026-10-21)

### Features

* **bff:** expose le gain Elo et les ratings d'avatar ([be2d42e](https://github.com/joutio/joutio-bff/commit/be2d42e253af18418129146436d220d260ad9055))

## [11.37.0](https://github.com/joutio/joutio-bff/compare/v11.36.0...v11.37.0) (2026-10-21)

### Features

* **bff:** expose l'Elo et l'apparie par rating ([ba64535](https://github.com/joutio/joutio-bff/commit/ba6453583e82de066e7521f10085c549634b48fd))

## [11.36.0](https://github.com/joutio/joutio-bff/compare/v11.35.1...v11.36.0) (2026-10-21)

### Features

* **bff:** autorise l'entraînement bot malgré un salon privé ([9fddfb0](https://github.com/joutio/joutio-bff/commit/9fddfb0675a439df4f40dd1d62a078897867cb36))

## [11.35.1](https://github.com/joutio/joutio-bff/compare/v11.35.0...v11.35.1) (2026-10-21)

### Bug Fixes

* **bff:** aligne les contrats sur les dernières releases ([473484f](https://github.com/joutio/joutio-bff/commit/473484f92e5bc4650e870f65420a60c44517fd4c))

## [11.35.0](https://github.com/joutio/joutio-bff/compare/v11.34.4...v11.35.0) (2026-10-21)

### Features

* **bff:** garde anti-spam et quotas de défis et salons ([ffe3ccd](https://github.com/joutio/joutio-bff/commit/ffe3ccd7a76115569c3711158f4148e9442274b8))

## [11.34.4](https://github.com/joutio/joutio-bff/compare/v11.34.3...v11.34.4) (2026-10-21)

### Bug Fixes

* **bff:** marque les notifications lues sans pré-lecture de projection ([b1e889f](https://github.com/joutio/joutio-bff/commit/b1e889f62f4b549cab90188bb673175225efa817))

## [11.34.3](https://github.com/joutio/joutio-bff/compare/v11.34.2...v11.34.3) (2026-10-18)

### Bug Fixes

* **bff:** protège le résultat des duels et le profil bot ([bd46fd2](https://github.com/joutio/joutio-bff/commit/bd46fd2e1445f1ed02382b1e404f60fcb9dfc21a))

## [11.34.2](https://github.com/joutio/joutio-bff/compare/v11.34.1...v11.34.2) (2026-10-18)

### Bug Fixes

* **bff:** consume identity suspension reasons 2.9.0 ([6a9a380](https://github.com/joutio/joutio-bff/commit/6a9a380782309578e61df03f333dc1c7b0251038))

## [11.34.1](https://github.com/joutio/joutio-bff/compare/v11.34.0...v11.34.1) (2026-10-18)

### Bug Fixes

* **bff:** materialize SearchResponse content from distributed query ([f4ecae4](https://github.com/joutio/joutio-bff/commit/f4ecae4af0a80cefa64845e73ed643b5088dd56a))

## [11.34.0](https://github.com/joutio/joutio-bff/compare/v11.33.0...v11.34.0) (2026-10-18)

### Features

* **bff:** expose admin player catalogue ([fa1dc41](https://github.com/joutio/joutio-bff/commit/fa1dc4198f740ecfd40f6d3a9a97563418373f23))

## [11.33.0](https://github.com/joutio/joutio-bff/compare/v11.32.2...v11.33.0) (2026-10-18)

### Features

* **bff:** add user moderation admin API ([ce6f8fa](https://github.com/joutio/joutio-bff/commit/ce6f8faa09b136f5647d09db652bc4ca80ef8cce))

## [11.32.2](https://github.com/joutio/joutio-bff/compare/v11.32.1...v11.32.2) (2026-10-18)

### Bug Fixes

* **bff:** expose tous les grades atteints des titres ([50b2b77](https://github.com/joutio/joutio-bff/commit/50b2b77573e0d3acfadb60b8979a6fcbf5ca92d2))

## [11.32.1](https://github.com/joutio/joutio-bff/compare/v11.32.0...v11.32.1) (2026-10-17)

### Bug Fixes

* **bff:** invalide le cache profil au changement de titre ([160db8a](https://github.com/joutio/joutio-bff/commit/160db8a60c42d75bcdf1db3ca8b95aad3447ec05))

## [11.32.0](https://github.com/joutio/joutio-bff/compare/v11.31.0...v11.32.0) (2026-10-17)

### Features

* **bff:** expose les signalements et l'administration des questions ([24dae11](https://github.com/joutio/joutio-bff/commit/24dae11b984e7bcceb6ab3fafdc85308d590ed3c))

## [11.31.0](https://github.com/joutio/joutio-bff/compare/v11.30.0...v11.31.0) (2026-10-17)

### Features

* **bff:** expose l'objectif d'un badge dans la célébration ([d6e8d7f](https://github.com/joutio/joutio-bff/commit/d6e8d7ffc40199612672260c683ba21d7fd0e2c7))
* **bff:** ouvre les invitations de defi sur la carte Social ([0ea896b](https://github.com/joutio/joutio-bff/commit/0ea896bb9c9b7f8f9bdc6ff55ca91f9ae340d783))

## [11.30.0](https://github.com/joutio/joutio-bff/compare/v11.29.0...v11.30.0) (2026-10-17)

### Features

* **bff:** débloque le titre Fer après une partie humaine terminée ([a61bbe8](https://github.com/joutio/joutio-bff/commit/a61bbe80c7291a910d4b82c899636c1e16de08e1))

## [11.29.0](https://github.com/joutio/joutio-bff/compare/v11.28.0...v11.29.0) (2026-10-17)

### Features

* **bff:** expose le rang des titres ([6c23ccf](https://github.com/joutio/joutio-bff/commit/6c23ccfcb341cc7ca86c3769e6d192221728f9bb))

## [11.28.0](https://github.com/joutio/joutio-bff/compare/v11.27.0...v11.28.0) (2026-10-16)

### Features

* **bff:** relance « wizz » de l'adversaire absent (endpoint, push, temps reel) ([1492361](https://github.com/joutio/joutio-bff/commit/1492361e0b9ca30e571cf839e9e65c264e8a711d))

## [11.27.0](https://github.com/joutio/joutio-bff/compare/v11.26.1...v11.27.0) (2026-10-16)

### Features

* **bff:** titre Novice des le premier duel joue ([fd379e7](https://github.com/joutio/joutio-bff/commit/fd379e78fa0670abc601a44fae03b320ddc7cc44))

## [11.26.1](https://github.com/joutio/joutio-bff/compare/v11.26.0...v11.26.1) (2026-10-16)

### Bug Fixes

* **bff:** pin profile 4.5.0 (famille de badges VICTORY) ([2fbea37](https://github.com/joutio/joutio-bff/commit/2fbea37eaaad91f75a3d1ff0a2bcac944a821667))

## [11.26.0](https://github.com/joutio/joutio-bff/compare/v11.25.0...v11.26.0) (2026-10-16)

### Features

* **bff:** historique des parties filtrable et triable ([ee468f0](https://github.com/joutio/joutio-bff/commit/ee468f0a57a0c733197d716d7f866835f472bf9d))

## [11.25.0](https://github.com/joutio/joutio-bff/compare/v11.24.0...v11.25.0) (2026-10-15)

### Features

* **bff:** titres thematiques de la fiche sujet dans l apercu ([45254ab](https://github.com/joutio/joutio-bff/commit/45254ab29fc2de29ba47419f2f9fd26da777cf71))

## [11.24.0](https://github.com/joutio/joutio-bff/compare/v11.23.0...v11.24.0) (2026-10-15)

### Features

* **bff:** fan-out ephemere des deblocages (badge, niveau, titre) ([34c5805](https://github.com/joutio/joutio-bff/commit/34c58053a43cb3174011e1f39233916da56b98df))

## [11.23.0](https://github.com/joutio/joutio-bff/compare/v11.22.0...v11.23.0) (2026-10-15)

### Features

* **bff:** six paliers de badges (pin profile-domain 4.3.0) ([2b3da47](https://github.com/joutio/joutio-bff/commit/2b3da4786b1dc7f2f52f10de81d8abf3dcf1484b))

## [11.22.0](https://github.com/joutio/joutio-bff/compare/v11.21.0...v11.22.0) (2026-10-15)

### Features

* **bff:** categorie des sujets dans les suggestions de la palette ([cf66979](https://github.com/joutio/joutio-bff/commit/cf66979d03aa1a1a1928a8a9b442e66fc8295942))

## [11.21.0](https://github.com/joutio/joutio-bff/compare/v11.20.0...v11.21.0) (2026-10-15)

### Features

* **bff:** badge Vision (metrique IMAGE_CORRECT) ([056dc0f](https://github.com/joutio/joutio-bff/commit/056dc0f28ea5965cbcde8ea96700ec198e51dded))

## [11.20.0](https://github.com/joutio/joutio-bff/compare/v11.19.0...v11.20.0) (2026-10-15)

### Features

* **bff:** badges Social et Defis ([bb6ea57](https://github.com/joutio/joutio-bff/commit/bb6ea572e5b88070266883471bcce0a71f12b85b))

## [11.19.0](https://github.com/joutio/joutio-bff/compare/v11.18.0...v11.19.0) (2026-10-15)

### Features

* **bff:** badge Assiduite (metrique DAILY_STREAK) ([dfbe6bf](https://github.com/joutio/joutio-bff/commit/dfbe6bf6e70d1ff7faba992f1bc241e5c84180a5))

## [11.18.0](https://github.com/joutio/joutio-bff/compare/v11.17.0...v11.18.0) (2026-10-15)

### Features

* **bff:** badges Precision et Rapidite ([1f741bc](https://github.com/joutio/joutio-bff/commit/1f741bcc2883dac3f8c004c1b7554b2fe63f3fce))

## [11.17.0](https://github.com/joutio/joutio-bff/compare/v11.16.0...v11.17.0) (2026-10-15)

### Features

* **bff:** badge de serie (metrique WIN_STREAK) ([50e0f32](https://github.com/joutio/joutio-bff/commit/50e0f322d0f114e6d6d3de7d7a765fd6ba2ab68e))

## [11.16.0](https://github.com/joutio/joutio-bff/compare/v11.15.0...v11.16.0) (2026-10-15)

### Features

* **bff:** expose les titres thématiques du sujet (TopicRefView.titles) ([52deb54](https://github.com/joutio/joutio-bff/commit/52deb544357ef49048c7153e8ef2058669229a52))

## [11.15.0](https://github.com/joutio/joutio-bff/compare/v11.14.1...v11.15.0) (2026-10-15)

### Features

* **bff:** badges d'exploration (métrique DISTINCT_TOPICS) ([cfe9f51](https://github.com/joutio/joutio-bff/commit/cfe9f5194f7921e1c3b40e38d8bfc7102e7302f0))

## [11.14.1](https://github.com/joutio/joutio-bff/compare/v11.14.0...v11.14.1) (2026-10-15)

### Bug Fixes

* **bff:** pin joutio-matchmaking-domain 10.4.0 -> 11.0.0 ([e5294cb](https://github.com/joutio/joutio-bff/commit/e5294cbfe2a9c6100878db16ee6d0a8815efc09b))

## [11.14.0](https://github.com/joutio/joutio-bff/compare/v11.13.0...v11.14.0) (2026-10-15)

### Features

* **bff:** ticket d'appariement EXPIRED (fin de recherche à 10 s) ([59a4035](https://github.com/joutio/joutio-bff/commit/59a4035432f2e0b6608e46e0d5392f715ee5a232))

## [11.13.0](https://github.com/joutio/joutio-bff/compare/v11.12.0...v11.13.0) (2026-10-14)

### Features

* **progression:** collection de badges (catalogue complet et progression) ([4d3f035](https://github.com/joutio/joutio-bff/commit/4d3f035a5aa8c83392fcd471dcb75c5fb283e5bd))

## [11.12.0](https://github.com/joutio/joutio-bff/compare/v11.11.0...v11.12.0) (2026-10-14)

### Features

* **progression:** maîtrise par sujet, titres thématiques et badges enrichis ([46fcc14](https://github.com/joutio/joutio-bff/commit/46fcc14f715fea58b1df6cde53a450b74d4697d9))

## [11.11.0](https://github.com/joutio/joutio-bff/compare/v11.10.0...v11.11.0) (2026-10-14)

### Features

* **bff:** accueil « les plus joués en ce moment » sur 24 h + tri MOST_PLAYED ([7e89204](https://github.com/joutio/joutio-bff/commit/7e8920403c6fb42b5f80e1ac6c4815018a7e4a15))

## [11.10.0](https://github.com/joutio/joutio-bff/compare/v11.9.0...v11.10.0) (2026-10-13)

### Features

* **bff:** répartition des parties par sujet (GET /api/profiles/{id}/topics) ([c1ee9dc](https://github.com/joutio/joutio-bff/commit/c1ee9dc75f7b50caaf4156da5329cd7961cb01c2))

## [11.9.0](https://github.com/joutio/joutio-bff/compare/v11.8.1...v11.9.0) (2026-10-13)

### Features

* **bff:** console d'administration média MinIO (liste, orphelins, purge, quota) ([094dcdc](https://github.com/joutio/joutio-bff/commit/094dcdcbedef1b8822affc685ba9b5008bb30219))

## [11.8.1](https://github.com/joutio/joutio-bff/compare/v11.8.0...v11.8.1) (2026-10-12)

### Bug Fixes

* **bff:** credentials S3 média aliasés depuis le secret (JOUTIO_MEDIA_ACCESS_KEY/SECRET_KEY) ([1ef4eef](https://github.com/joutio/joutio-bff/commit/1ef4eeff4c47074d97a686d8eca40836edff29a0))

## [11.8.0](https://github.com/joutio/joutio-bff/compare/v11.7.1...v11.8.0) (2026-10-12)

### Features

* **bff:** upload d'images (MinIO) + bannieres sujet/profil ([c564faa](https://github.com/joutio/joutio-bff/commit/c564faa26744625605e10e72b96361181fa94354))

## [11.7.1](https://github.com/joutio/joutio-bff/compare/v11.7.0...v11.7.1) (2026-10-12)

### Bug Fixes

* **bff:** contrats domain alignés sur les versions publiées (identity 2.5, theme 5.4, social 5.2, game 8.3, profile 3.4, matchmaking 10.2, leaderboard 4.2, notification 3.4) ([095ffda](https://github.com/joutio/joutio-bff/commit/095ffda5e168220e43de4df2cdc06d75f8123376))

## [11.7.0](https://github.com/joutio/joutio-bff/compare/v11.6.0...v11.7.0) (2026-10-12)

### Features

* **console:** consume joutio-sdk 4.7.0 (lag, commandes en vol, snapshots, scheduled) ([18dc235](https://github.com/joutio/joutio-bff/commit/18dc235f042531fd88a17bea32a2b037587f5c20))

## [11.6.0](https://github.com/joutio/joutio-bff/compare/v11.5.2...v11.6.0) (2026-10-12)

### Features

* **console:** consume joutio-sdk 4.6.0 (build-info + API interne event store/scheduled) ([51b870e](https://github.com/joutio/joutio-bff/commit/51b870e2776d570418ac381eadd0be936858205f))

## [11.5.2](https://github.com/joutio/joutio-bff/compare/v11.5.1...v11.5.2) (2026-10-12)

### Bug Fixes

* **bff:** SDK 4.5.2 — groupes instance-scopés démarrés à la queue du flux ([fa55cd3](https://github.com/joutio/joutio-bff/commit/fa55cd3b3c8e056aea3b4c2d09455a2ae3cce120))

## [11.5.1](https://github.com/joutio/joutio-bff/compare/v11.5.0...v11.5.1) (2026-10-12)

### Bug Fixes

* **bff:** invalidation cross-réplicas des caches de lecture (follows, profil) ([207b9ad](https://github.com/joutio/joutio-bff/commit/207b9ad13c1bbcbed66f2f311a430564fd666255))

## [11.5.0](https://github.com/joutio/joutio-bff/compare/v11.4.1...v11.5.0) (2026-10-12)

### Features

* **bff:** prépare RoomPlayer et la couverture linguistique dans les commandes ([b7fba1f](https://github.com/joutio/joutio-bff/commit/b7fba1feee18c2789c2cbec472ba426690b914a4))

## [11.4.1](https://github.com/joutio/joutio-bff/compare/v11.4.0...v11.4.1) (2026-10-11)

### Bug Fixes

* **bff:** ne pas empoisonner le cache de profil juste après une écriture ([351b833](https://github.com/joutio/joutio-bff/commit/351b8333890ce8c0635da1a0100bf8a94675636f))

## [11.4.0](https://github.com/joutio/joutio-bff/compare/v11.3.0...v11.4.0) (2026-10-11)

### Features

* **bff:** notification ROOM_PLAYED_QUESTIONS_LOADED (pin matchmaking 9.2.0) ([38386ef](https://github.com/joutio/joutio-bff/commit/38386ef496a000fc6ba1221dae00bb948ec27cd1))

## [11.3.0](https://github.com/joutio/joutio-bff/compare/v11.2.0...v11.3.0) (2026-10-11)

### Features

* **bff:** notification de salle ROOM_GAME_CREATED (pin matchmaking 9.1.0) ([d1b4c4c](https://github.com/joutio/joutio-bff/commit/d1b4c4c99a7312655661531b274ddf61cd2e56a1))

## [11.2.0](https://github.com/joutio/joutio-bff/compare/v11.1.1...v11.2.0) (2026-10-11)

### Features

* **bff:** caches progression et compteurs de follows (hot path /api/me) ([36012c1](https://github.com/joutio/joutio-bff/commit/36012c14b0a214939208117a59dea037f153f499))

## [11.1.1](https://github.com/joutio/joutio-bff/compare/v11.1.0...v11.1.1) (2026-10-11)

### Bug Fixes

* **bff:** fan-out multi-réplique branché sur la source Kafka (SDK 4.4.1) ([7943516](https://github.com/joutio/joutio-bff/commit/7943516544d16c42675029d69fe2884138d37ec5))

## [11.1.0](https://github.com/joutio/joutio-bff/compare/v11.0.1...v11.1.0) (2026-10-11)

### Features

* **bff:** fan-out temps réel multi-réplique (groupes instance-scopés, Web Push global) ([f76d48a](https://github.com/joutio/joutio-bff/commit/f76d48a4f5b47012efe73834497ce046f9d17854))

## [11.0.1](https://github.com/joutio/joutio-bff/compare/v11.0.0...v11.0.1) (2026-10-11)

### Bug Fixes

* **bff:** en-têtes CORS sur le 401 unknownUser (purge de session visible du navigateur) ([51fc758](https://github.com/joutio/joutio-bff/commit/51fc758e016a5c7155f16c0410043f748c7a5c97))

## [11.0.0](https://github.com/joutio/joutio-bff/compare/v10.0.0...v11.0.0) (2026-10-11)

### ⚠ BREAKING CHANGES

* **bff:** RoomView/RoomNotification sans readyDeadlineAt (partie créée immédiatement)

### Features

* **bff:** RoomView/RoomNotification sans readyDeadlineAt (partie créée immédiatement) ([42d17b1](https://github.com/joutio/joutio-bff/commit/42d17b193eeb1d5092088551bf7da926acf0ad37))

## [10.0.0](https://github.com/joutio/joutio-bff/compare/v9.0.0...v10.0.0) (2026-10-11)

### ⚠ BREAKING CHANGES

* **bff:** payloads de notifications (deadlineAt déplacée) et corps POST /api/challenges étendu.

### Features

* **bff:** revanche (gameId validé), annulation de salon, countdown de recherche ([0af4e04](https://github.com/joutio/joutio-bff/commit/0af4e045ad40dad0fd3257d6c830b2a7e4431397))

## [9.0.0](https://github.com/joutio/joutio-bff/compare/v8.0.0...v9.0.0) (2026-10-11)

### ⚠ BREAKING CHANGES

* **bff:** POST /api/games retiré ; MatchmakingMatchedEvent.gameId → roomId.

### Features

* **bff:** la salle est l'unique point d'entrée de préparation d'une partie ([4e57e2f](https://github.com/joutio/joutio-bff/commit/4e57e2fc821ebbd0eeee88dd396fd21d40c6efa5))

## [8.0.0](https://github.com/joutio/joutio-bff/compare/v7.0.0...v8.0.0) (2026-10-10)

### ⚠ BREAKING CHANGES

* **bff:** endpoint /api/profiles/{id}/activity retiré de la surface BFF.

### Features

* **bff:** retire l'endpoint d'activité joueur (MVP) ([1f42ead](https://github.com/joutio/joutio-bff/commit/1f42ead17960a7ecc09db6d10903db300b36a4da))

## [7.0.0](https://github.com/joutio/joutio-bff/compare/v6.3.0...v7.0.0) (2026-10-10)

### ⚠ BREAKING CHANGES

* **bff:** routes, topics WS et DTOs changés de bout en bout.

### Features

* **bff:** surface /api/rooms, collection games active, résultat enrichi ([9042c32](https://github.com/joutio/joutio-bff/commit/9042c325f365cd71ae211c2eae82e2d14d9ca2d4))

## [6.3.0](https://github.com/joutio/joutio-bff/compare/v6.2.0...v6.3.0) (2026-10-10)

### Features

* **bff:** notifications non lues et push haute urgence ([4909aad](https://github.com/joutio/joutio-bff/commit/4909aad4123b9fc8f57e828121fdf858be844fb8))

## [6.2.0](https://github.com/joutio/joutio-bff/compare/v6.1.0...v6.2.0) (2026-10-09)

### Features

* **bff:** expose les noms de sujet multilingues (names) ([dacb227](https://github.com/joutio/joutio-bff/commit/dacb2271d80f802223da26074b4801be96e9e585))

## [6.1.0](https://github.com/joutio/joutio-bff/compare/v6.0.1...v6.1.0) (2026-10-09)

### Features

* **bff:** accueil newTopics (theme RECENT, limite 10) ([60f8210](https://github.com/joutio/joutio-bff/commit/60f8210c736f63abe661268c713bd959c7ad3747))

## [6.0.1](https://github.com/joutio/joutio-bff/compare/v6.0.0...v6.0.1) (2026-10-08)

### Bug Fixes

* **bff:** historique sans statuts CREATED/READY (game-domain 7.0.0) ([b649a7c](https://github.com/joutio/joutio-bff/commit/b649a7cfc22760eef103b47efb1e46481ef54c19))

## [6.0.0](https://github.com/joutio/joutio-bff/compare/v5.1.0...v6.0.0) (2026-10-08)

### ⚠ BREAKING CHANGES

* **bff:** retrait des endpoints join/leave et des notifications PLAYER_JOINED/LEFT ; pinne game-domain 7.0.0

### Features

* **bff:** retrait des endpoints join/leave et des notifications PLAYER_JOINED/LEFT ; pinne game-domain 7.0.0 ([59269f6](https://github.com/joutio/joutio-bff/commit/59269f6cdbb15b1e7e253428c705f544514ef63e))

## [5.1.0](https://github.com/joutio/joutio-bff/compare/v5.0.0...v5.1.0) (2026-10-08)

### Features

* **bff:** progression du theme = questions completees (query game) ; pinne game-domain 6.1.0 ([5ccaacb](https://github.com/joutio/joutio-bff/commit/5ccaacbb8c570a7bda6d8d8b453e65a2fbbf2974))

## [5.0.0](https://github.com/joutio/joutio-bff/compare/v4.14.0...v5.0.0) (2026-10-08)

### ⚠ BREAKING CHANGES

* **bff:** endpoints /api/games/{id}/rematch* supprimes.

### Features

* **bff:** resultat a l'instant de la partie et retrait du rematch ([8f4e9dc](https://github.com/joutio/joutio-bff/commit/8f4e9dc00694d7dd8706358396c337698bdab629))

## [4.14.0](https://github.com/joutio/joutio-bff/compare/v4.13.3...v4.14.0) (2026-10-08)

### Features

* **bff:** renouvellement des baux de session de presence ([5dbacef](https://github.com/joutio/joutio-bff/commit/5dbacefe06dc0bad585c32114d1f4c7915feda17))

## [4.13.3](https://github.com/joutio/joutio-bff/compare/v4.13.2...v4.13.3) (2026-10-07)

### Bug Fixes

* **deps:** aligner profile-domain 3.0.0 (retour post-revert) ([3f27fcd](https://github.com/joutio/joutio-bff/commit/3f27fcd25ed0145b010102f9108968f032db5380))

## [4.13.2](https://github.com/joutio/joutio-bff/compare/v4.13.1...v4.13.2) (2026-10-07)

### Reverts

* Revert "feat(presence): heartbeat batch des leases de session" ([009700a](https://github.com/joutio/joutio-bff/commit/009700aaa0f986bfd39bb4b60181d511a6166c6c))

## [4.13.1](https://github.com/joutio/joutio-bff/compare/v4.13.0...v4.13.1) (2026-10-07)

### Bug Fixes

* **deps:** pin theme 4.5.2 et matchmaking 4.3.0 (versions publiées) ([4f099b8](https://github.com/joutio/joutio-bff/commit/4f099b866a230c38b2a9ff6f265182ae6e5c60ac))

## [4.13.0](https://github.com/joutio/joutio-bff/compare/v4.12.0...v4.13.0) (2026-10-07)

### Features

* **presence:** heartbeat batch des leases de session ([c421d6f](https://github.com/joutio/joutio-bff/commit/c421d6fd1ba5a271286e168ef29e9a850745191b))

## [4.12.0](https://github.com/joutio/joutio-bff/compare/v4.11.2...v4.12.0) (2026-10-05)

### Features

* **bff:** ecran de resultat (xp) et endpoints revanche (game-domain 5.2.0) ([184b2e2](https://github.com/joutio/joutio-bff/commit/184b2e21eaeaf1669fd2a1b9cf760c9ae9749bd6))

## [4.11.2](https://github.com/joutio/joutio-bff/compare/v4.11.1...v4.11.2) (2026-10-05)

### Bug Fixes

* **bff:** pin theme-domain 4.4.0 (categorie RELIGION et seeds) ([9992b5a](https://github.com/joutio/joutio-bff/commit/9992b5a5ab5d1e2a9802c92e6eda3f1510fecf0f))

## [4.11.1](https://github.com/joutio/joutio-bff/compare/v4.11.0...v4.11.1) (2026-10-05)

### Bug Fixes

* **bff:** pin theme-domain 4.2.0 (categorie Religions) ([73556d5](https://github.com/joutio/joutio-bff/commit/73556d5873ce27f23e90581b59be29d6aba66f47))

## [4.11.0](https://github.com/joutio/joutio-bff/compare/v4.10.0...v4.11.0) (2026-10-05)

### Features

* **bff:** fan-out ephemere de presence aux abonnes (follow-presence) ([763f22d](https://github.com/joutio/joutio-bff/commit/763f22d1ba583d54bdc7d7d3c619802597d3d88c))

## [4.10.0](https://github.com/joutio/joutio-bff/compare/v4.9.0...v4.10.0) (2026-10-05)

### Features

* **bff:** notification ephemere LOBBY_LEFT (sortie non destructive) ([7e7af73](https://github.com/joutio/joutio-bff/commit/7e7af7320b6959cc08ff0f3a4244d474d659234e))

## [4.9.0](https://github.com/joutio/joutio-bff/compare/v4.8.1...v4.9.0) (2026-10-05)

### Features

* **bff:** suppression en masse de l'inbox (DELETE /api/notifications) et pin notification 2.5.0 ([959acf3](https://github.com/joutio/joutio-bff/commit/959acf32bc055ec24783fb2d881c39311395f9b1))

### Bug Fixes

* **bff:** partie courante absente en 204 au lieu d'un 404 ([47fa07e](https://github.com/joutio/joutio-bff/commit/47fa07e74950147f93df5fa563e75781b6b56855))

## [4.8.1](https://github.com/joutio/joutio-bff/compare/v4.8.0...v4.8.1) (2026-10-05)

### Bug Fixes

* **bff:** push du defi recu vers l'inbox ([6df3e40](https://github.com/joutio/joutio-bff/commit/6df3e402adba8a2d781dd1bfdc09f65a187d30cb))

## [4.8.0](https://github.com/joutio/joutio-bff/compare/v4.7.0...v4.8.0) (2026-10-05)

### Features

* **bff:** historique de duel — issues WAITING/IN_PROGRESS/CANCELLED ([ae78a8d](https://github.com/joutio/joutio-bff/commit/ae78a8dd44947f2777b925acea378f023b9f6c00))

## [4.7.0](https://github.com/joutio/joutio-bff/compare/v4.6.1...v4.7.0) (2026-10-05)

### Features

* **bff:** surface des defis nominatifs (/api/challenges) ([3f15033](https://github.com/joutio/joutio-bff/commit/3f15033fbb3e7297023f4e735657a6c0bdd74738))

## [4.6.1](https://github.com/joutio/joutio-bff/compare/v4.6.0...v4.6.1) (2026-10-05)

### Bug Fixes

* **bff:** reprise — test du endpoint /api/games/current ([7981f17](https://github.com/joutio/joutio-bff/commit/7981f17bc93d999abe6ba02e9b05e0181789b865))

## [4.6.0](https://github.com/joutio/joutio-bff/compare/v4.5.1...v4.6.0) (2026-10-05)

### Features

* **bff:** vue de reprise GET /api/games/current ([317a759](https://github.com/joutio/joutio-bff/commit/317a75927a171a1cea5fecb44b97f3292d58d61f))

## [4.5.1](https://github.com/joutio/joutio-bff/compare/v4.5.0...v4.5.1) (2026-10-05)

### Bug Fixes

* **bff:** embarque notification-domain 2.3.0 (LOBBY_MISSED) ([5297f73](https://github.com/joutio/joutio-bff/commit/5297f73e16114b8051ae53e7ea60c13fd827420b))

## [4.5.0](https://github.com/joutio/joutio-bff/compare/v4.4.2...v4.5.0) (2026-10-05)

### Features

* **bff:** salle temps reelle (enter, phase, presence) ([c4f656a](https://github.com/joutio/joutio-bff/commit/c4f656a896769330ffb6a43f9d9c9adbdfcb2988))

## [4.4.2](https://github.com/joutio/joutio-bff/compare/v4.4.1...v4.4.2) (2026-10-05)

### Bug Fixes

* **bff:** deep link du defi accepte vers l'arene ([8df3050](https://github.com/joutio/joutio-bff/commit/8df30505f5aec928d0a3832daa0c56128bd25fe6))

## [4.4.1](https://github.com/joutio/joutio-bff/compare/v4.4.0...v4.4.1) (2026-10-05)

### Bug Fixes

* **bff:** enregistre le provider BouncyCastle du web push ([6cde211](https://github.com/joutio/joutio-bff/commit/6cde211d34d1771422a179b021195716ed9a59d5))

## [4.4.0](https://github.com/joutio/joutio-bff/compare/v4.3.0...v4.4.0) (2026-10-05)

### Features

* **bff:** web push des notifications (abonnements VAPID + envoi) ([7ac039e](https://github.com/joutio/joutio-bff/commit/7ac039ec9468cc7324a5cfc8592f3241a0d2eaf5))

## [4.3.0](https://github.com/joutio/joutio-bff/compare/v4.2.0...v4.3.0) (2026-10-04)

### Features

* **bff:** suppression des notifications (DELETE + push NOTIFICATION_DELETED) ([d83d598](https://github.com/joutio/joutio-bff/commit/d83d5980c46d2bf64df4e724f0e41e4ca04c8c6e))

## [4.2.0](https://github.com/joutio/joutio-bff/compare/v4.1.1...v4.2.0) (2026-10-03)

### Features

* **bff:** topic authoring surface ([3e3fb01](https://github.com/joutio/joutio-bff/commit/3e3fb01fd3b468047d548b9c863c85b6441953c0))

## [4.1.1](https://github.com/joutio/joutio-bff/compare/v4.1.0...v4.1.1) (2026-10-03)

### Bug Fixes

* **bff:** invalidate profile read cache on updates ([cda159c](https://github.com/joutio/joutio-bff/commit/cda159c2c9847ce08f3547d684f383508243edde))

## [4.1.0](https://github.com/joutio/joutio-bff/compare/v4.0.0...v4.1.0) (2026-10-03)

### Features

* **bff:** notification inbox, nominative challenge and released domain pins ([769bb6a](https://github.com/joutio/joutio-bff/commit/769bb6ae127618436b1613be52f2cb4f8ecc079e))

## [4.0.0](https://github.com/joutio/joutio-bff/compare/v3.0.0...v4.0.0) (2026-10-02)

### ⚠ BREAKING CHANGES

* **bff:** matchmaking tickets + private lobbies surface

### Code Refactoring

* **bff:** matchmaking tickets + private lobbies surface ([c4f2d55](https://github.com/joutio/joutio-bff/commit/c4f2d552c23ede7c614bcbf5f3051df166257372))

## [3.0.0](https://github.com/joutio/joutio-bff/compare/v2.2.1...v3.0.0) (2026-09-30)

### ⚠ BREAKING CHANGES

* **bff:** profile updates are per-field endpoints (/pseudonym, /bio, /country, /avatar-options, /language); displayName renamed to pseudonym; MeView exposes language; RoundStartedNotification exposes translations; game creation passes the creator language (pins SDK 4.1.0, profile 3.0.0, theme 4.0.0, game 4.0.0, social 3.0.0, matchmaking 3.0.0, leaderboard 3.0.0).

### Features

* **bff:** per-field profile endpoints, pseudonym rename and language-aware games ([2dfea1c](https://github.com/joutio/joutio-bff/commit/2dfea1c51cc084a15f49f04d869254542d5aaeac))

## [2.2.1](https://github.com/joutio/joutio-bff/compare/v2.2.0...v2.2.1) (2026-09-28)

### Bug Fixes

* **bff:** reject stale sessions with 401 ([67429c1](https://github.com/joutio/joutio-bff/commit/67429c1e2b47291276c11e452820141feec31373))

## [2.2.0](https://github.com/joutio/joutio-bff/compare/v2.1.0...v2.2.0) (2026-09-27)

### Features

* **bff:** expose topic cover image in topic views ([f60af56](https://github.com/joutio/joutio-bff/commit/f60af5648d626dbadafaa6b7955710e60a27d328))

## [2.1.0](https://github.com/joutio/joutio-bff/compare/v2.0.0...v2.1.0) (2026-09-27)

### Features

* **bff:** hybrid REST/WS surface, dedicated views and resilient presence ([448f918](https://github.com/joutio/joutio-bff/commit/448f918beb7d92967604d949319ed92a1106d881))

## [2.0.0](https://github.com/joutio/joutio-bff/compare/v1.2.4...v2.0.0) (2026-09-25)

### ⚠ BREAKING CHANGES

* **bff:** all application traffic now goes through the BFF; the business
services are headless.

### Features

* **bff:** unique REST/WS surface for the headless services ([bb3f2a6](https://github.com/joutio/joutio-bff/commit/bb3f2a65ddf26caefe75fa09c89cee4dad19b09c))

## [1.2.4](https://github.com/joutio/joutio-bff/compare/v1.2.3...v1.2.4) (2026-09-24)

### Bug Fixes

* **deps:** bump joutio-parent to 2.4.4 (typed PageResult over query transport) ([862a3de](https://github.com/joutio/joutio-bff/commit/862a3de16e71df2fca300feb98458673d7dc345c))

## [1.2.3](https://github.com/joutio/joutio-bff/compare/v1.2.2...v1.2.3) (2026-09-24)

### Bug Fixes

* **bff:** read untyped rows for paginated followers (query-bus element erasure) ([a5fc431](https://github.com/joutio/joutio-bff/commit/a5fc43130051c620780c727d953cbd3dc3b489fa))

## [1.2.2](https://github.com/joutio/joutio-bff/compare/v1.2.1...v1.2.2) (2026-09-24)

### Bug Fixes

* **deps:** bump joutio-parent to 2.4.3 (bus-only search criteria type info) ([13a97b4](https://github.com/joutio/joutio-bff/commit/13a97b467176550cf7c3744d216c6c7da3231da9))

## [1.2.1](https://github.com/joutio/joutio-bff/compare/v1.2.0...v1.2.1) (2026-09-24)

### Bug Fixes

* **deps:** bump joutio-parent to 2.4.2 (search criteria type info) ([adbb16d](https://github.com/joutio/joutio-bff/commit/adbb16d8a7abbe7d6996fa7628a3d69b2d02cb11))

## [1.2.0](https://github.com/joutio/joutio-bff/compare/v1.1.0...v1.2.0) (2026-09-24)

### Features

* **bff:** resolve profiles in batch (GetProfilesByIds) with cache ([1abff2d](https://github.com/joutio/joutio-bff/commit/1abff2d1e370e13f99219cf560558de742a06a48))

## [1.1.0](https://github.com/joutio/joutio-bff/compare/v1.0.0...v1.1.0) (2026-09-24)

### Features

* **bff:** matchmaking queue endpoints (enqueue/ticket/cancel) ([46903af](https://github.com/joutio/joutio-bff/commit/46903afd925e4561967464c032315fdce9e65222))

## 1.0.0 (2026-09-24)

### Features

* **bff:** initial joutio-bff facade (REST resources + realtime fan-out) ([e308de0](https://github.com/joutio/joutio-bff/commit/e308de05c2d2f1eccfd2cc8d4c9a07f1a2755abd))
