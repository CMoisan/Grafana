# 06 — Préparation à l'entretien Solutions Engineer

Un Solutions Engineer chez Grafana Labs n'est ni un commercial ni un ingénieur
support. Son métier : **traduire un problème client en architecture Grafana**,
le démontrer, et défendre les compromis techniques.

On évaluera trois choses :
1. votre **maîtrise technique** de la stack,
2. votre capacité à **expliquer simplement** des concepts complexes,
3. votre **aisance en démo** — savoir raconter ce qui se passe à l'écran.

Ce lab est conçu pour couvrir les trois.

---

## La démo de 15 minutes, minute par minute

Lancez `.\scripts\05-k6.ps1 simulation` et suivez ce déroulé. Entraînez-vous à
voix haute, trois fois.

### 0–2 min — Poser le contexte

> « Voici une API de commandes, deux instances sur Kubernetes. Aucun agent
> installé dans l'application : elle expose simplement un endpoint `/metrics` et
> écrit ses logs sur la sortie standard. Tout le reste est fait par Alloy. »

Montrez l'**UI d'Alloy** (port 12345), onglet *Graph*.

> « Un seul agent remplace Prometheus, Promtail et l'OTel Collector. Voici le
> pipeline : découverte des pods, relabeling, scrape, envoi vers Mimir. Et en
> parallèle : lecture des logs, parsing JSON, envoi vers Loki. »

**Pourquoi commencer par là** : cela montre que vous comprenez la collecte, pas
seulement les jolis graphes. C'est ce que le client aura le plus de mal à faire
seul.

### 2–5 min — Le dashboard RED

> « RED, c'est la méthode standard pour un service : Rate, Errors, Duration. »

Pointez chaque tuile. Puis le panneau *Trafic par endpoint* :

> « Regardez le label : `/api/orders/{id}`, avec les accolades. C'est le
> **template**, pas l'URL réelle. Si on y mettait l'identifiant de commande,
> chaque commande créerait une série temporelle. C'est l'explosion de
> cardinalité — la première cause de facture qui dérape et de plateforme qui
> s'écroule. »

Puis le chiffre d'affaires encaissé :

> « Et ça, c'est la métrique qui intéresse votre direction. Une plateforme
> d'observabilité qui ne parle que de CPU ne se vend pas ; celle qui affiche le
> chiffre d'affaires en temps réel, si. »

### 5–8 min — La panne (elle arrive toute seule à T+6)

> « Le test de charge va injecter une panne dans quelques secondes. Regardez le
> p99. »

Laissez le graphe décrocher.

> « La latence monte. Notez que les erreurs, elles, ne bougent pas encore. C'est
> systématique : la latence est un signal précoce, les erreurs un signal tardif.
> C'est pour ça qu'on alerte d'abord sur la latence. »

### 8–11 min — La corrélation

Passez en **Split view**, Loki à droite, même fenêtre de temps :

```logql
{namespace="apps", app="orders-api"} | json | level="WARN"
```

> « Le log est là, à la seconde près : `event=chaos_updated`. La métrique m'a dit
> **quand** et **combien**. Le log me dit **pourquoi**. C'est toute la valeur
> d'avoir les deux signaux dans la même interface, avec les mêmes labels. »

Insistez :

> « Ce n'est possible que parce que la configuration Alloy impose exactement les
> mêmes noms de labels — `namespace`, `pod`, `app` — aux métriques et aux logs.
> C'est une décision de conception, pas de la magie. »

### 11–13 min — L'alerte et le SLO

Onglet **Alerting** :

> « L'alerte est passée en Pending, puis Firing après deux minutes. Le `for`
> évite qu'un pic d'une seconde réveille l'astreinte. »

Puis la règle de burn rate :

> « Celle-ci est plus intéressante. Notre SLO est de 99 % sur 30 jours, soit 1 %
> de budget d'erreur. Cette alerte se déclenche quand on consomme le budget
> 14 fois trop vite — le mois serait épuisé en deux jours. Deux fenêtres : une
> longue pour éviter les faux positifs, une courte pour que l'alerte se referme
> dès que c'est réparé. »

### 13–15 min — La réparation et la conclusion

La panne se répare automatiquement à T+11. Montrez le retour au vert.

> « Ce qu'on vient de voir n'est pas une démonstration de produit, c'est un cycle
> d'exploitation complet : détecter, diagnostiquer, corréler, résoudre, vérifier.
> Et tout est décrit en fichiers versionnés — datasources, dashboards, règles
> d'alerte. Aucun clic dans l'interface. »

---

## Questions techniques et réponses argumentées

### « Pourquoi Mimir plutôt que Prometheus ? »

> Prometheus est excellent, et Mimir n'existe que pour lever ses trois limites
> structurelles : la **rétention** (Prometheus est conçu pour quelques semaines
> de données locales), la **haute disponibilité** (deux Prometheus en parallèle
> produisent des données dupliquées et légèrement décalées), et l'**isolation
> multi-équipes**.
>
> Mimir garde exactement le même langage et le même protocole d'ingestion, mais
> découple le calcul du stockage : les blocs partent sur S3, et on scale les
> queriers indépendamment des ingesters. Concrètement, un client migre sans
> changer une seule requête ni un seul dashboard.

### « Comment expliquez-vous la cardinalité à un client ? »

> Chaque combinaison unique de labels crée une série temporelle stockée
> séparément. `http_requests{method="GET", status="200"}` et
> `{method="POST", status="200"}` sont deux séries distinctes.
>
> Le calcul se fait en multipliant : 10 endpoints × 5 méthodes × 8 codes de
> statut × 20 pods = 8 000 séries. Ça va. Mais ajoutez un `user_id` avec
> 100 000 valeurs et vous passez à 800 millions. La plateforme tombe, et la
> facture aussi.
>
> La règle : un label doit avoir un nombre de valeurs **borné et connu à
> l'avance**. Identifiants, e-mails, URLs brutes, timestamps n'ont rien à faire
> en label — ils vont dans les logs ou les traces.

### « Métriques, logs ou traces : par où commencer ? »

> Toujours les **métriques**. Elles sont peu coûteuses, agrégées, et répondent à
> « est-ce que ça va ? » et « depuis quand ? ». C'est ce qui permet de détecter.
>
> Ensuite les **logs**, pour le « pourquoi » sur un cas précis.
>
> Les **traces** en dernier : elles répondent à « où, dans ma chaîne de
> 15 microservices ? ». Très puissantes, mais elles n'ont de sens que si
> l'architecture est distribuée et que la première étape est déjà en place.
>
> Un client qui veut commencer par les traces se trompe presque toujours de
> problème.

### « Comment réduire la facture d'un client Grafana Cloud ? »

Par ordre d'impact :

> 1. **Supprimer les métriques jamais requêtées.** Grafana Cloud fournit des
>    *usage insights* qui les listent. C'est souvent 30 à 50 % du volume.
> 2. **Réduire la cardinalité** : retirer les labels inutiles au relabeling,
>    plafonner côté application.
> 3. **Allonger l'intervalle de scrape** sur ce qui bouge lentement : passer de
>    15 s à 60 s divise le volume par 4.
> 4. **Ne pas ingérer les logs DEBUG en production**, et échantillonner les logs
>    à très haut volume.
> 5. **Recording rules + rétention différenciée** : garder l'agrégat un an, le
>    détail deux semaines.
>
> Le conseil de fond : décider ce qu'on **n'ingère pas** est la première
> optimisation, bien avant d'optimiser ce qu'on ingère.

### « Un client dit que ses dashboards sont lents. Que faites-vous ? »

> D'abord je mesure au lieu de deviner : le *query inspector* de Grafana donne le
> temps par requête, et Mimir expose ses propres métriques de latence de query.
>
> Ensuite, dans l'ordre :
> - des **recording rules** pour tout ce qui est recalculé à chaque affichage —
>   c'est le gain le plus important, souvent un facteur 50 à 200 ;
> - vérifier qu'aucune requête ne s'exécute sans nom de métrique, ce qui force à
>   parcourir tous les blocs ;
> - `$__rate_interval` au lieu d'intervalles figés, pour ne pas
>   sur-échantillonner quand l'utilisateur dézoome sur 30 jours ;
> - côté plateforme, le cache et le *query sharding* du query-frontend, puis
>   scaler les queriers.

### « Quelle différence entre liveness et readiness ? »

> La liveness répond à « es-tu vivant ? » — si non, Kubernetes **tue et recrée**
> le pod. La readiness répond à « peux-tu servir maintenant ? » — si non, le pod
> est simplement **retiré du service**, mais reste en vie.
>
> L'erreur classique est de brancher une dépendance externe sur la liveness : la
> base de données tombe, tous les pods échouent leur liveness, Kubernetes les
> redémarre en boucle, et une panne partielle devient une panne totale.
>
> Les dépendances vont sur la **readiness**. Et pour les applications lentes à
> démarrer comme une JVM, on ajoute une **startup probe**, qui suspend les deux
> autres pendant le démarrage.

### « Pourquoi votre service Java n'a-t-il aucune annotation Spring ? »

> Parce que la logique métier ne devrait dépendre d'aucun framework. Le service
> reçoit ses dépendances par constructeur, sous forme d'interfaces que **lui**
> définit : c'est l'inversion de dépendance.
>
> Trois bénéfices concrets : mes tests unitaires tournent en millisecondes sans
> contexte Spring ; je remplace la persistance ou la passerelle de paiement en
> changeant une ligne de configuration ; et j'ai même défini un port
> `OrderMetrics`, ce qui me permet de **tester que mes métriques métier sont bien
> émises**. Chez nous, casser une métrique casse le build.

---

## Le portefeuille Grafana en une phrase chacun

| Produit | Une phrase |
|---|---|
| **Grafana** | La couche de visualisation, agnostique : des dizaines de datasources, y compris celles des concurrents. |
| **Mimir** | Métriques Prometheus à l'échelle : rétention longue, haute dispo, multi-tenant. |
| **Loki** | Logs indexés par labels uniquement — coût d'ingestion très inférieur à un moteur full-text. |
| **Tempo** | Traces distribuées sur stockage objet, sans index — donc peu coûteuses à conserver. |
| **Pyroscope** | Profiling continu : *quelle ligne de code* consomme le CPU en production. |
| **Alloy** | Le collecteur unique, distribution Grafana d'OpenTelemetry. |
| **k6** | Tests de charge scriptés, intégrés à la plateforme. |
| **Beyla** | Auto-instrumentation eBPF : des métriques RED sans toucher au code. |
| **OnCall / IRM** | Astreintes et gestion d'incidents. |

**L'argument différenciant à savoir formuler** : le *big tent*. Grafana ne
demande pas de tout remplacer. Vous gardez votre Prometheus, votre Datadog,
votre Elasticsearch, et Grafana les affiche côte à côte. La migration se fait
brique par brique, sans big bang. C'est ce qui rend l'adoption possible dans des
organisations qui ne peuvent pas tout changer d'un coup.

---

## Questions à POSER pendant l'entretien

Poser de bonnes questions compte autant que répondre.

- « Quel est le profil type de vos clients en France ? Plutôt de la migration 
  depuis une stack propriétaire (type Datadog/Dynatrace), ou de la consolidation
  d'outils Open Source fragmentés ? »
- « Sur quoi butent le plus souvent les POCs — la gestion de la cardinalité/coûts
  d'ingestion, la migration de l'historique et des dashboards, ou l'adoption par
  les équipes dev/SRE ? »
- « Comment se fait le passage de relais entre le Solutions Engineer et le
  Support/Customer Success une fois le compte en production ? »
- « Quelle est la répartition réelle entre l'avant-vente pure (qualif, POC,
  démo) et l'accompagnement post-vente (adoption, comptes clés) ? »

---

## Auto-évaluation

Vous êtes prêt quand vous savez, **sans notes** :

- [ ] dessiner le chemin d'une métrique de l'application jusqu'au dashboard ;
- [ ] expliquer counter / gauge / histogram et quand utiliser chacun ;
- [ ] écrire un p95 correct et dire pourquoi la version « moyennée » est fausse ;
- [ ] expliquer la cardinalité avec un exemple chiffré ;
- [ ] écrire une requête LogQL avec parsing et filtre, et dire pourquoi l'ordre
      des opérations change la performance ;
- [ ] définir SLI / SLO / budget d'erreur et calculer un burn rate ;
- [ ] justifier `constant-arrival-rate` plutôt que `constant-vus` ;
- [ ] citer les trois modes de déploiement de Loki et quand choisir chacun ;
- [ ] dérouler la démo de 15 minutes sans regarder vos notes.
