# Exercices — parcours de formation Solutions Engineer

Sept environnements, du plus simple au plus proche du métier réel. Chacun a un
**énoncé**, des **critères de réussite vérifiables**, et une réponse à la question
*« qu'est-ce que ça prouve en entretien ? »*.

Le lab principal (à la racine du dépôt) est l'**environnement de référence** :
il est complet et fonctionnel. Les exercices le dégradent, le fragmentent ou le
mettent en situation pour créer un problème à résoudre.

---

## Le parcours

| # | Exercice | Durée | Ce que ça travaille |
|---|---|---|---|
| [01](01-instrumenter-de-zero/ENONCE.md) | Instrumenter de zéro | 3–4 h | Micrometer, découverte Alloy, dashboard RED |
| [02](02-incident-de-cardinalite/ENONCE.md) | ⭐ Incident de cardinalité | 2–3 h | Diagnostic sous pression, garde-fous, post-mortem |
| [03](03-dev-et-prod/ENONCE.md) | ⭐⭐ Dev et prod | 1–2 j | Multi-tenant, SLO différenciés, promotion de dashboards |
| [04](04-migration-prometheus/ENONCE.md) | Migration Prometheus | ½–1 j | Conduite du changement, réversibilité |
| [05](05-legacy-sans-metriques/ENONCE.md) | Legacy sans métriques | 3–4 h | Métriques dérivées des logs, honnêteté sur les limites |
| [06](06-reduction-de-cout/ENONCE.md) | ⭐⭐ Réduction de coût | 4–6 h | Mesurer, réduire, chiffrer pour un décideur |
| [07](07-discovery-et-demo/ENONCE.md) | ⭐⭐⭐ Discovery et démo | 1 j ×3 | Le métier entier : qualifier, cadrer, démontrer |

**Total : environ 4 à 5 jours de travail effectif.**

---

## Dans quel ordre

**Si vous avez le temps** : 01 → 02 → 03 → 04 → 05 → 06 → 07.
L'ordre est progressif, chaque exercice réutilise les précédents.

**Si vous n'avez que trois jours** : **02, 03, 06, 07**.
Ce sont les quatre qui produisent une histoire racontable en entretien.
Les trois autres construisent des compétences, ceux-là construisent des preuves.

**Si vous n'avez qu'un jour** : **07 seul**, en vous appuyant sur le lab de
référence tel quel. C'est l'exercice qui adresse le vrai trou du profil —
le contexte commercial, pas la technique.

---

## La règle qui vaut pour les sept

> **Écrivez la question avant de regarder la donnée.**

Chaque exercice commence par formuler ce qu'on cherche à savoir. C'est la
différence entre un ingénieur qui construit un dashboard de 40 panneaux que
personne ne regarde, et un SE qui construit les 6 panneaux qui répondent à la
question que le client s'est posée.

---

## Un mot sur les critères de réussite

Ils sont volontairement **vérifiables**, pas déclaratifs. « Comprendre la
cardinalité » n'est pas un critère ; « donner le nombre de séries avant et après,
avec le facteur de réduction » en est un.

C'est la même exigence qu'un PoC client : sans critère de réussite écrit à
l'avance, un PoC ne se termine jamais, ou il échoue à la fin sur un critère
inventé par quelqu'un qui n'était pas dans la boucle.

---

## État d'avancement

- [x] Énoncés rédigés
- [ ] 01 — environnement construit
- [ ] 02 — environnement construit
- [ ] 03 — environnement construit
- [ ] 04 — environnement construit
- [ ] 05 — environnement construit
- [ ] 06 — environnement construit
- [ ] 07 — brief prêt (l'environnement est le lab de référence)
