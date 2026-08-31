package com.grafanalab.orders.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * ============================================================================
 * REGLAGES FINS DE L'INSTRUMENTATION. Beaucoup de valeur pedagogique ici.
 * ============================================================================
 */
@Configuration
public class ObservabilityConfig {

    /**
     * COMMON TAGS : ajoutes automatiquement a TOUTES les metriques de l'appli.
     *
     * Pourquoi c'est important : dans Mimir vous aurez plusieurs applis, plusieurs
     * environnements, plusieurs versions. Sans ces labels, impossible de filtrer
     * ni de comparer une v1 et une v2 pendant un deploiement canari.
     *
     * ATTENTION : chaque common tag MULTIPLIE le nombre de series. On en met
     * peu, et uniquement des valeurs stables. Jamais le nom du pod ici :
     * Alloy l'ajoute deja au moment du scrape (label `pod`), et un pod qui
     * redemarre cree une nouvelle serie a chaque fois.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags(
            @Value("${spring.application.name:orders-api}") String appName,
            @Value("${app.environment:dev}") String environment,
            @Value("${app.version:1.0.0}") String version) {
        return registry -> registry.config().commonTags(
                "application", appName,
                "environment", environment,
                "version", version);
    }

    /**
     * HISTOGRAMMES ET SLO SUR LES REQUETES HTTP.
     *
     * Par defaut Spring publie `http_server_requests_seconds_count` et `_sum`
     * (donc seulement une MOYENNE). Une moyenne de latence ne veut rien dire :
     * elle cache les utilisateurs qui souffrent. Il faut des BUCKETS.
     *
     * `percentilesHistogram` -> expose des buckets `_bucket{le="..."}` que
     * Mimir peut agreger entre pods, et que PromQL exploite via :
     *
     *   histogram_quantile(0.95,
     *     sum by (le) (rate(http_server_requests_seconds_bucket[5m])))
     *
     * `serviceLevelObjectives` -> ajoute des buckets EXACTS a 100ms / 300ms /
     * 1s / 3s. Ces bornes precises permettent de calculer un vrai SLO du type
     * "99% des requetes sous 300 ms" sans erreur d'interpolation :
     *
     *   sum(rate(http_server_requests_seconds_bucket{le="0.3"}[5m]))
     *   / sum(rate(http_server_requests_seconds_count[5m]))
     *
     * COUT : chaque bucket = une serie temporelle. Un histogramme complet peut
     * generer 20-40 series par combinaison uri/method/status. C'est le compromis
     * classique cardinalite / precision, et un vrai sujet de discussion client.
     */
    @Bean
    public MeterFilter httpHistograms() {
        return new MeterFilter() {
            @Override
            public io.micrometer.core.instrument.distribution.DistributionStatisticConfig configure(
                    io.micrometer.core.instrument.Meter.Id id,
                    io.micrometer.core.instrument.distribution.DistributionStatisticConfig config) {

                if (!id.getName().startsWith("http.server.requests")) {
                    return config;
                }
                return io.micrometer.core.instrument.distribution.DistributionStatisticConfig.builder()
                        .percentilesHistogram(true)
                        .serviceLevelObjectives(
                                Duration.ofMillis(100).toNanos(),
                                Duration.ofMillis(300).toNanos(),
                                Duration.ofSeconds(1).toNanos(),
                                Duration.ofSeconds(3).toNanos())
                        // Le cast en double est important : les surcharges qui prennent
                        // un Long sont DEPRECIEES dans Micrometer. Sans le cast, le build
                        // affiche un avertissement de depreciation.
                        .minimumExpectedValue((double) Duration.ofMillis(1).toNanos())
                        .maximumExpectedValue((double) Duration.ofSeconds(10).toNanos())
                        .build()
                        .merge(config);
            }
        };
    }

    /**
     * GARDE-FOU ANTI-CARDINALITE.
     *
     * `MeterFilter.maximumAllowableTags` plafonne le nombre de valeurs distinctes
     * d'un label : au-dela, tout est regroupe sous "OTHER" au lieu de creer des
     * series a l'infini. C'est une ceinture de securite : si demain quelqu'un
     * ajoute un endpoint qui met un ID dans l'URI, votre Mimir survivra.
     *
     * En entretien, savoir citer ce filtre = vous avez deja vecu un incident
     * de cardinalite.
     */
    @Bean
    public MeterFilter cardinalityGuard() {
        return MeterFilter.maximumAllowableTags(
                "http.server.requests", "uri", 100, MeterFilter.deny());
    }
}
