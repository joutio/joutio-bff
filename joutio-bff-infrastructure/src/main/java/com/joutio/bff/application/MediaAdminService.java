package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaAssetView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaDeleteResultView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaObjectView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaObjectsView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaOrphansView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaPurgeView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaStatsView;
import com.joutio.bff.infrastructure.in.api.response.AdminMediaUsageView;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import com.joutio.bff.infrastructure.out.media.MediaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Administration du stockage média (console d'exploitation) : listing du bucket (uploads du BFF +
 * images du seed), statistiques, usage par joueur, détection d'orphelins S3 ↔ {@code media_asset}
 * et suppression/purge. Toute suppression d'objet vérifie au préalable les références métier
 * (theme/profile) ; en cas d'indisponibilité de la vérification, l'opération échoue fermée (503).
 */
@Service
public class MediaAdminService {

    private static final Logger logger = LoggerFactory.getLogger(MediaAdminService.class);

    static final int DEFAULT_LIST_SIZE = 50;
    static final int MAX_LIST_SIZE = 200;
    static final int DEFAULT_USAGE_SIZE = 50;
    static final int MAX_USAGE_SIZE = 200;

    /** Listing S3 : pagination par curseur, pages de 1000 clés maximum. */
    private static final int SCAN_PAGE_SIZE = 1000;
    /** Garde-fou du scan complet (stats/orphelins) : 50 pages de 1000 objets. */
    private static final int MAX_SCAN_PAGES = 50;

    private final MediaProperties properties;
    private final MediaStorage storage;
    private final MediaAssetRepository repository;
    private final MediaReferenceChecker referenceChecker;

    public MediaAdminService(MediaProperties properties,
                             MediaStorage storage,
                             MediaAssetRepository repository,
                             MediaReferenceChecker referenceChecker) {
        this.properties = properties;
        this.storage = storage;
        this.repository = repository;
        this.referenceChecker = referenceChecker;
    }

    /** Page d'objets du bucket, enrichie des lignes {@code media_asset} et des références métier. */
    public CompletableFuture<AdminMediaObjectsView> list(MediaOrigin origin, String query, String cursor, int size) {
        int pageSize = clamp(size, DEFAULT_LIST_SIZE, MAX_LIST_SIZE);
        String prefix = origin == null ? null : origin.prefix();
        MediaObjectPage page = storage.listObjects(prefix, blankToNull(cursor), pageSize);
        List<MediaObject> objects = filterByQuery(page.objects(), query);
        if (objects.isEmpty()) {
            return CompletableFuture.completedFuture(new AdminMediaObjectsView(List.of(), page.nextContinuationToken()));
        }
        Map<String, List<MediaAsset>> assetsByKey = repository.findByObjectKeys(keysOf(objects)).stream()
                .collect(Collectors.groupingBy(MediaAsset::objectKey));
        List<String> urls = objects.stream().map(object -> urlFor(object.objectKey())).toList();
        return referenceChecker.countReferences(urls)
                .handle((references, error) -> {
                    Map<String, Long> counts = error == null ? references : null;
                    if (error != null) {
                        logger.warn("Références média indisponibles pour {} objet(s) listé(s)", objects.size(), error);
                    }
                    List<AdminMediaObjectView> views = objects.stream()
                            .map(object -> toObjectView(object,
                                    assetsByKey.getOrDefault(object.objectKey(), List.of()),
                                    counts == null ? null : counts.getOrDefault(urlFor(object.objectKey()), 0L)))
                            .toList();
                    return new AdminMediaObjectsView(views, page.nextContinuationToken());
                });
    }

    /** Statistiques du bucket et du catalogue, avec orphelins dans les deux sens. */
    public AdminMediaStatsView stats() {
        BucketScan scan = scanBucket();
        List<MediaAsset> assets = repository.findAllOrdered();
        Set<String> assetKeys = assets.stream().map(MediaAsset::objectKey).collect(Collectors.toSet());
        Set<String> bucketKeys = scan.objects().stream().map(MediaObject::objectKey).collect(Collectors.toSet());

        List<MediaObject> orphanObjects = orphanObjects(scan.objects(), assetKeys);
        List<MediaAsset> danglingAssets = assets.stream()
                .filter(asset -> !bucketKeys.contains(asset.objectKey()))
                .toList();
        long owners = assets.stream().map(MediaAsset::uploadedBy).distinct().count();
        return new AdminMediaStatsView(
                bucketStats(scan.objects()),
                new AdminMediaStatsView.AssetStats(assets.size(),
                        assets.stream().mapToLong(MediaAsset::sizeBytes).sum(), owners),
                orphanObjects.size(),
                orphanObjects.stream().mapToLong(MediaObject::sizeBytes).sum(),
                danglingAssets.size(),
                scan.truncated());
    }

    /** Orphelins détaillés : objets {@code uploads/} sans ligne, lignes sans objet. */
    public AdminMediaOrphansView orphans() {
        BucketScan scan = scanBucket();
        List<MediaAsset> assets = repository.findAllOrdered();
        Set<String> assetKeys = assets.stream().map(MediaAsset::objectKey).collect(Collectors.toSet());
        Set<String> bucketKeys = scan.objects().stream().map(MediaObject::objectKey).collect(Collectors.toSet());
        return new AdminMediaOrphansView(
                orphanObjects(scan.objects(), assetKeys).stream()
                        .map(object -> toObjectView(object, List.of(), null))
                        .toList(),
                assets.stream()
                        .filter(asset -> !bucketKeys.contains(asset.objectKey()))
                        .map(MediaAdminService::toAssetView)
                        .toList());
    }

    /** Usage du stockage par joueur (volumétrie du quota), trié par octets décroissants. */
    public PageResponse<AdminMediaUsageView> usage(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = clamp(size, DEFAULT_USAGE_SIZE, MAX_USAGE_SIZE);
        long total = repository.countUploaders();
        List<AdminMediaUsageView> content = repository.usageByUploader(safePage, safeSize).stream()
                .map(usage -> new AdminMediaUsageView(usage.uploadedBy(), usage.assetCount(), usage.totalBytes()))
                .toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    /**
     * Supprime un objet du bucket et toutes les lignes {@code media_asset} qui le référencent.
     * Bloqué ({@code 409}) si des entités métier le référencent encore, sauf {@code force}.
     */
    public CompletableFuture<AdminMediaDeleteResultView> deleteObject(String objectKey, boolean force) {
        String key = requireKey(objectKey);
        List<MediaAsset> assets = repository.findByObjectKey(key);
        boolean exists = storage.exists(key);
        if (assets.isEmpty() && !exists) {
            throw new BffProblems.MediaObjectNotFoundProblem(key);
        }
        String url = urlFor(key);
        return referenceChecker.countReferences(List.of(url))
                .handle((references, error) -> {
                    if (error != null) {
                        logger.error("Vérification des références média indisponible pour {}", key, error);
                        throw new BffProblems.MediaReferenceUnavailableProblem(
                                "vérification des références impossible pour " + key);
                    }
                    return references.getOrDefault(url, 0L);
                })
                .thenApply(references -> {
                    if (references > 0 && !force) {
                        throw new BffProblems.MediaObjectReferencedProblem(key, references);
                    }
                    if (exists) {
                        storage.delete(key);
                    }
                    repository.deleteByObjectKey(key);
                    logger.info("Suppression admin média {} : {} objet(s), {} ligne(s) media_asset (force={})",
                            key, exists ? 1 : 0, assets.size(), force);
                    return new AdminMediaDeleteResultView(exists ? 1 : 0, assets.size());
                });
    }

    /** Supprime une ligne {@code media_asset} orpheline (objet déjà absent du bucket). */
    public AdminMediaDeleteResultView deleteAsset(UUID mediaId) {
        MediaAsset asset = repository.findById(mediaId)
                .orElseThrow(() -> new BffProblems.MediaNotFoundProblem(mediaId.toString()));
        repository.delete(asset);
        logger.info("Suppression admin de la ligne média {} (objet {})", mediaId, asset.objectKey());
        return new AdminMediaDeleteResultView(0, 1);
    }

    /**
     * Purge les orphelins : objets {@code uploads/} sans ligne et lignes sans objet. Un objet
     * encore référencé par une entité métier est conservé (jamais de suppression silencieuse de
     * contenu attaché) ; la vérification indisponible bloque la purge (503).
     */
    public CompletableFuture<AdminMediaPurgeView> purgeOrphans() {
        BucketScan scan = scanBucket();
        List<MediaAsset> assets = repository.findAllOrdered();
        Set<String> assetKeys = assets.stream().map(MediaAsset::objectKey).collect(Collectors.toSet());
        Set<String> bucketKeys = scan.objects().stream().map(MediaObject::objectKey).collect(Collectors.toSet());
        List<MediaObject> orphanObjects = orphanObjects(scan.objects(), assetKeys);
        List<MediaAsset> danglingAssets = assets.stream()
                .filter(asset -> !bucketKeys.contains(asset.objectKey()))
                .toList();
        List<String> orphanUrls = orphanObjects.stream().map(object -> urlFor(object.objectKey())).toList();
        return referenceChecker.countReferences(orphanUrls)
                .handle((references, error) -> {
                    if (error != null) {
                        logger.error("Purge d'orphelins refusée : vérification des références indisponible", error);
                        throw new BffProblems.MediaReferenceUnavailableProblem(
                                "vérification des références impossible, purge refusée");
                    }
                    int deletedObjects = 0;
                    int skipped = 0;
                    for (MediaObject object : orphanObjects) {
                        if (references.getOrDefault(urlFor(object.objectKey()), 0L) > 0) {
                            skipped++;
                            continue;
                        }
                        storage.delete(object.objectKey());
                        deletedObjects++;
                    }
                    danglingAssets.forEach(repository::delete);
                    logger.info("Purge admin orphelins : {} objet(s), {} ligne(s), {} objet(s) référencé(s) conservé(s)",
                            deletedObjects, danglingAssets.size(), skipped);
                    return new AdminMediaPurgeView(deletedObjects, danglingAssets.size(), skipped);
                });
    }

    private BucketScan scanBucket() {
        List<MediaObject> objects = new ArrayList<>();
        String token = null;
        int pages = 0;
        while (pages < MAX_SCAN_PAGES) {
            MediaObjectPage page = storage.listObjects(null, token, SCAN_PAGE_SIZE);
            objects.addAll(page.objects());
            token = page.nextContinuationToken();
            pages++;
            if (token == null) {
                break;
            }
        }
        return new BucketScan(objects, token != null);
    }

    private static List<MediaObject> orphanObjects(List<MediaObject> objects, Set<String> assetKeys) {
        return objects.stream()
                .filter(object -> MediaOrigin.UPLOAD == MediaOrigin.of(object.objectKey()))
                .filter(object -> !assetKeys.contains(object.objectKey()))
                .toList();
    }

    private static List<AdminMediaStatsView.BucketStats> bucketStats(List<MediaObject> objects) {
        return objects.stream()
                .collect(Collectors.groupingBy(object -> originLabel(object.objectKey())))
                .entrySet().stream()
                .map(entry -> new AdminMediaStatsView.BucketStats(entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().mapToLong(MediaObject::sizeBytes).sum()))
                .sorted((first, second) -> first.origin().compareTo(second.origin()))
                .toList();
    }

    private AdminMediaObjectView toObjectView(MediaObject object, List<MediaAsset> assets, Long referenceCount) {
        return new AdminMediaObjectView(
                object.objectKey(),
                originLabel(object.objectKey()),
                urlFor(object.objectKey()),
                object.sizeBytes(),
                object.lastModified(),
                assets.size(),
                assets.stream().map(MediaAsset::uploadedBy).distinct().toList(),
                referenceCount);
    }

    private static AdminMediaAssetView toAssetView(MediaAsset asset) {
        return new AdminMediaAssetView(asset.id(), asset.objectKey(), asset.url(), asset.uploadedBy(),
                asset.sizeBytes(), asset.createdAt());
    }

    private static String originLabel(String objectKey) {
        MediaOrigin origin = MediaOrigin.of(objectKey);
        return origin == null ? "OTHER" : origin.name();
    }

    private String urlFor(String objectKey) {
        return MediaPublicUrl.urlFor(properties, objectKey);
    }

    private static String requireKey(String objectKey) {
        String key = blankToNull(objectKey);
        if (key == null) {
            throw new BffProblems.InvalidMediaAdminRequestProblem("la clé d'objet est requise");
        }
        return key;
    }

    private static List<String> keysOf(List<MediaObject> objects) {
        return objects.stream().map(MediaObject::objectKey).toList();
    }

    private static List<MediaObject> filterByQuery(List<MediaObject> objects, String query) {
        String needle = blankToNull(query);
        if (needle == null) {
            return objects;
        }
        String lowered = needle.toLowerCase(Locale.ROOT);
        return objects.stream()
                .filter(object -> object.objectKey().toLowerCase(Locale.ROOT).contains(lowered))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int clamp(int value, int defaultValue, int max) {
        int base = value <= 0 ? defaultValue : value;
        return Math.min(base, max);
    }

    private record BucketScan(List<MediaObject> objects, boolean truncated) {
    }
}
