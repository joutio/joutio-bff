package com.joutio.bff.infrastructure.config;

import com.joutio.bff.infrastructure.out.media.MediaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

/**
 * Client S3 du stockage média (MinIO-compatible) : endpoint/bucket/clés par configuration, transport
 * HTTP JDK (`url-connection-client`, aucun binaire natif — compatible arm64), adressage `path-style`
 * requis par MinIO. Les credentials statiques ne sont utilisés que si les clés sont fournies
 * (en prod : SealedSecret `joutio-bff-media`), sinon la chaîne AWS par défaut s'applique.
 */
@Configuration
@EnableConfigurationProperties(MediaProperties.class)
public class MediaConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MediaConfiguration.class);

    @Bean
    public S3Client mediaS3Client(MediaProperties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.s3().region() == null ? "us-east-1" : properties.s3().region()))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .forcePathStyle(properties.s3().pathStyle());
        if (hasText(properties.s3().endpoint())) {
            builder.endpointOverride(URI.create(properties.s3().endpoint()));
        }
        if (hasText(properties.s3().accessKey()) && hasText(properties.s3().secretKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    properties.s3().accessKey(), properties.s3().secretKey())));
        } else {
            // Sans credentials statiques, l'upload échouera à la première requête (chaîne AWS par
            // défaut) : le signaler explicitement plutôt que de le découvrir sur un 503.
            logger.warn("Stockage média sans credentials statiques (joutio.media.s3.access-key/secret-key) : "
                    + "les uploads échoueront si aucune chaîne AWS par défaut n'est disponible");
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
