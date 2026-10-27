package com.joutio.bff.infrastructure.in.api.problem;

import com.joutio.microservice.core.domain.exception.BaseProblem;
import com.joutio.microservice.core.domain.exception.ProblemCategory;

import java.util.Map;

/**
 * Problèmes de façade propres au BFF (validation de requête, ressource absente).
 */
public final class BffProblems {

    private BffProblems() {
    }

    /** Les styles d'avatar DiceBear sont réservés aux comptes Premium (admin inclus). */
    public static class PremiumRequiredProblem extends BaseProblem {
        public PremiumRequiredProblem() {
            super("urn:joutio:authorization:premiumRequired",
                    ProblemCategory.PERMISSION,
                    "Premium required",
                    "Cette fonctionnalité est réservée aux comptes Premium",
                    Map.of());
        }
    }

    /** Micah est débloqué par la récompense d'assiduité (ou Premium/admin). */
    public static class MicahLockedProblem extends BaseProblem {
        public MicahLockedProblem() {
            super("urn:joutio:authorization:micahLocked",
                    ProblemCategory.PERMISSION,
                    "Micah locked",
                    "Le style Micah se débloque après 7 jours d'activité consécutive",
                    Map.of());
        }
    }

    /** Centres d'intérêt invalides (plus de 3, doublon, code inconnu). */
    public static class InvalidInterestsProblem extends BaseProblem {
        public InvalidInterestsProblem(String detail) {
            super("urn:joutio:bff:profile:invalidInterests",
                    ProblemCategory.VALIDATION,
                    "Invalid interests",
                    detail,
                    Map.of());
        }
    }

    public static class InvalidAvatarOptionsProblem extends BaseProblem {
        public InvalidAvatarOptionsProblem() {
            super("urn:joutio:bff:profile:invalidAvatarOptions",
                    ProblemCategory.VALIDATION,
                    "Invalid avatar options",
                    "Les options d'avatar sont invalides",
                    Map.of());
        }
    }

    public static class InvalidAccountDeletionCodeProblem extends BaseProblem {
        public InvalidAccountDeletionCodeProblem() {
            super("urn:joutio:bff:account:invalidDeletionCode",
                    ProblemCategory.VALIDATION,
                    "Invalid account deletion code",
                    "The account deletion code is invalid, expired or already used",
                    Map.of());
        }
    }

    public static class InvalidGameRequestProblem extends BaseProblem {
        public InvalidGameRequestProblem(String detail) {
            super("urn:joutio:bff:game:invalidRequest",
                    ProblemCategory.VALIDATION,
                    "Invalid game request",
                    detail,
                    Map.of());
        }
    }

    public static class InvalidGameListRequestProblem extends BaseProblem {
        public InvalidGameListRequestProblem(String detail) {
            super("urn:joutio:bff:game:invalidListRequest",
                    ProblemCategory.VALIDATION,
                    "Invalid game list request",
                    detail,
                    Map.of());
        }
    }

    /** Le lecteur n'est pas l'un des deux joueurs de la partie (résultat / revue privés). */
    public static class GameAccessDeniedProblem extends BaseProblem {
        public GameAccessDeniedProblem(String gameId) {
            super("urn:joutio:bff:game:accessDenied",
                    ProblemCategory.PERMISSION,
                    "Game access denied",
                    "Only the two players of the game can access its result",
                    Map.of("gameId", gameId));
        }
    }

    /** Fenêtre d'activité invalide (ordre des dates ou plus d'un an). */
    public static class InvalidActivityRangeProblem extends BaseProblem {
        public InvalidActivityRangeProblem() {
            super("urn:joutio:bff:profile:invalidActivityRange",
                    ProblemCategory.VALIDATION,
                    "Invalid activity range",
                    "The activity range must be ordered and span at most 366 days",
                    Map.of());
        }
    }

    public static class InvalidTopicListRequestProblem extends BaseProblem {
        public InvalidTopicListRequestProblem(String detail) {
            super("urn:joutio:bff:topic:invalidListRequest",
                    ProblemCategory.VALIDATION,
                    "Invalid topic list request",
                    detail,
                    Map.of());
        }
    }

    public static class InvalidQuestionRequestProblem extends BaseProblem {
        public InvalidQuestionRequestProblem(String detail) {
            super("urn:joutio:bff:question:invalidRequest",
                    ProblemCategory.VALIDATION,
                    "Invalid question request",
                    detail,
                    Map.of());
        }
    }

    public static class PushDisabledProblem extends BaseProblem {
        public PushDisabledProblem() {
            super("urn:joutio:bff:push:disabled",
                    ProblemCategory.BUSINESS_RESOURCE_MISSING,
                    "Web Push disabled",
                    "Web Push is not configured on this server",
                    Map.of());
        }
    }

    public static class NotTopicOwnerProblem extends BaseProblem {
        public NotTopicOwnerProblem(String topicId) {
            super("urn:joutio:bff:topic:notOwner",
                    ProblemCategory.PERMISSION,
                    "Topic management not allowed",
                    "Only the creator of the topic can manage it",
                    Map.of("topicId", topicId));
        }
    }

    /** Signalement d'une question refusé (partie non terminée, joueur non participant…). */
    public static class QuestionReportNotAllowedProblem extends BaseProblem {
        public QuestionReportNotAllowedProblem(String questionId, String gameId, String detail) {
            super("urn:joutio:bff:question:reportNotAllowed",
                    ProblemCategory.PERMISSION,
                    "Question report not allowed",
                    detail,
                    Map.of("questionId", questionId, "gameId", gameId));
        }
    }

    /** Paramètres invalides d'un signalement de question (`400`). */
    public static class InvalidQuestionReportRequestProblem extends BaseProblem {
        public InvalidQuestionReportRequestProblem(String detail) {
            super("urn:joutio:bff:question:reportInvalid",
                    ProblemCategory.VALIDATION,
                    "Invalid question report",
                    detail,
                    Map.of());
        }
    }

    /** Signalement d'un joueur refusé (auto-signalement, compte protégé, partie incohérente…). */
    public static class UserReportNotAllowedProblem extends BaseProblem {
        public UserReportNotAllowedProblem(String targetUserId, String detail) {
            super("urn:joutio:bff:user:reportNotAllowed",
                    ProblemCategory.PERMISSION,
                    "User report not allowed",
                    detail,
                    Map.of("targetUserId", targetUserId));
        }
    }

    /** Paramètres invalides d'un signalement de joueur (`400`). */
    public static class InvalidUserReportRequestProblem extends BaseProblem {
        public InvalidUserReportRequestProblem(String detail) {
            super("urn:joutio:bff:user:reportInvalid",
                    ProblemCategory.VALIDATION,
                    "Invalid user report",
                    detail,
                    Map.of());
        }
    }

    /** Paramètres invalides d'une suspension (`400`). */
    public static class InvalidSuspensionRequestProblem extends BaseProblem {
        public InvalidSuspensionRequestProblem(String detail) {
            super("urn:joutio:bff:user:invalidSuspension",
                    ProblemCategory.VALIDATION,
                    "Invalid suspension",
                    detail,
                    Map.of());
        }
    }

    /** Compte déjà suspendu (`409`). */
    public static class UserAlreadySuspendedProblem extends BaseProblem {
        public UserAlreadySuspendedProblem(String userId) {
            super("urn:joutio:bff:user:alreadySuspended",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "User already suspended",
                    "The account " + userId + " is already suspended",
                    Map.of("userId", userId));
        }
    }

    /** Compte système protégé (`403`). */
    public static class ProtectedAccountProblem extends BaseProblem {
        public ProtectedAccountProblem(String userId) {
            super("urn:joutio:bff:user:protectedAccount",
                    ProblemCategory.PERMISSION,
                    "Protected account",
                    "The system account cannot be moderated",
                    Map.of("userId", userId));
        }
    }

    /** Défi refusé : l'adversaire est suspendu (`409`). */
    public static class ChallengeOpponentSuspendedProblem extends BaseProblem {
        public ChallengeOpponentSuspendedProblem(String opponentId) {
            super("urn:joutio:bff:challenge:opponentSuspended",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Opponent suspended",
                    "The opponent " + opponentId + " is suspended",
                    Map.of("opponentId", opponentId));
        }
    }

    /** Défi interdit : l'adversaire a supprimé son compte. */
    public static class ChallengeOpponentDeletedProblem extends BaseProblem {
        public ChallengeOpponentDeletedProblem(String opponentId) {
            super("urn:joutio:bff:challenge:opponentDeleted",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Opponent deleted",
                    "The opponent " + opponentId + " has deleted their account",
                    Map.of("opponentId", opponentId));
        }
    }

    /** Signalement interdit : la cible a supprimé son compte. */
    public static class UserReportTargetDeletedProblem extends BaseProblem {
        public UserReportTargetDeletedProblem(String targetUserId) {
            super("urn:joutio:bff:userReport:targetDeleted",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Target account deleted",
                    "The user " + targetUserId + " has deleted their account",
                    Map.of("targetUserId", targetUserId));
        }
    }

    /** Le challenger n'a pas joué la partie d'origine de la revanche. */
    public static class RematchNotAllowedProblem extends BaseProblem {        public RematchNotAllowedProblem(String gameId, String playerId) {
            super("urn:joutio:bff:challenge:rematchNotAllowed",
                    ProblemCategory.PERMISSION,
                    "Rematch not allowed",
                    "The player " + playerId + " did not play the game " + gameId,
                    Map.of("gameId", gameId, "playerId", playerId));
        }
    }

    /** Partie d'origine inéligible (bot) ou adversaire incohérent. */
    public static class InvalidRematchProblem extends BaseProblem {
        public InvalidRematchProblem(String detail) {
            super("urn:joutio:bff:challenge:invalidRematch",
                    ProblemCategory.VALIDATION,
                    "Invalid rematch",
                    detail,
                    Map.of());
        }
    }

    /** Aucun fichier fourni (part manquante ou vide). */
    public static class MediaFileMissingProblem extends BaseProblem {
        public MediaFileMissingProblem() {
            super("urn:joutio:bff:media:fileMissing",
                    ProblemCategory.VALIDATION,
                    "Missing media file",
                    "The multipart part 'file' is required and must not be empty",
                    Map.of());
        }
    }

    /** Format non supporté : seuls JPEG et PNG sont acceptés (détection par contenu). */
    public static class MediaUnsupportedFormatProblem extends BaseProblem {
        public MediaUnsupportedFormatProblem() {
            super("urn:joutio:bff:media:unsupportedFormat",
                    ProblemCategory.VALIDATION,
                    "Unsupported media format",
                    "Only JPEG and PNG images are supported",
                    Map.of());
        }
    }

    /** Contenu indécodable ou dimensions/pixels hors bornes. */
    public static class MediaInvalidImageProblem extends BaseProblem {
        public MediaInvalidImageProblem(String detail) {
            super("urn:joutio:bff:media:invalidImage",
                    ProblemCategory.VALIDATION,
                    "Invalid media image",
                    detail,
                    Map.of());
        }
    }

    /** Fichier au-delà de {@code joutio.media.max-file-size} (mappé 413). */
    public static class MediaTooLargeProblem extends BaseProblem {
        public MediaTooLargeProblem(String detail) {
            super("urn:joutio:bff:media:tooLarge",
                    ProblemCategory.VALIDATION,
                    "Media file too large",
                    detail,
                    Map.of());
        }
    }

    /** Quota d'images de l'utilisateur atteint (mappé 429). */
    public static class MediaQuotaExceededProblem extends BaseProblem {
        public MediaQuotaExceededProblem(String detail) {
            super("urn:joutio:bff:media:quotaExceeded",
                    ProblemCategory.VALIDATION,
                    "Media quota exceeded",
                    detail,
                    Map.of());
        }
    }

    /** Stockage objet injoignable ou non configuré (mappé 503). */
    public static class MediaStorageUnavailableProblem extends BaseProblem {
        public MediaStorageUnavailableProblem(String detail) {
            super("urn:joutio:bff:media:storageUnavailable",
                    ProblemCategory.TECHNICAL,
                    "Media storage unavailable",
                    detail,
                    Map.of());
        }
    }

    /** Image inconnue du catalogue média (`404`). */
    public static class MediaNotFoundProblem extends BaseProblem {
        public MediaNotFoundProblem(String mediaId) {
            super("urn:joutio:bff:media:notFound",
                    ProblemCategory.BUSINESS_RESOURCE_MISSING,
                    "Media not found",
                    "No media with id " + mediaId,
                    Map.of("mediaId", mediaId));
        }
    }

    /** Suppression d'une image dont l'utilisateur n'est pas l'auteur (`403`). */
    public static class MediaNotOwnerProblem extends BaseProblem {
        public MediaNotOwnerProblem(String mediaId) {
            super("urn:joutio:bff:media:notOwner",
                    ProblemCategory.PERMISSION,
                    "Media deletion not allowed",
                    "Only the uploader can delete the media " + mediaId,
                    Map.of("mediaId", mediaId));
        }
    }

    /** Paramètres invalides d'une requête d'administration média (`400`). */
    public static class InvalidMediaAdminRequestProblem extends BaseProblem {
        public InvalidMediaAdminRequestProblem(String detail) {
            super("urn:joutio:bff:media:invalidAdminRequest",
                    ProblemCategory.VALIDATION,
                    "Invalid media admin request",
                    detail,
                    Map.of());
        }
    }

    /** Aucun objet S3 ni ligne {@code media_asset} pour cette clé (`404`). */
    public static class MediaObjectNotFoundProblem extends BaseProblem {
        public MediaObjectNotFoundProblem(String objectKey) {
            super("urn:joutio:bff:media:objectNotFound",
                    ProblemCategory.BUSINESS_RESOURCE_MISSING,
                    "Media object not found",
                    "No stored object or asset matches key " + objectKey,
                    Map.of("objectKey", objectKey));
        }
    }

    /** Objet encore référencé par une entité métier : suppression refusée sans `force` (`409`). */
    public static class MediaObjectReferencedProblem extends BaseProblem {
        public MediaObjectReferencedProblem(String objectKey, long references) {
            super("urn:joutio:bff:media:referenced",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Media object referenced",
                    "The object " + objectKey + " is referenced by " + references
                            + " entity(ies); use force=true to delete it anyway",
                    Map.of("objectKey", objectKey, "references", references));
        }
    }

    /** Vérification des références inter-services indisponible : purge refusée (`503`). */
    public static class MediaReferenceUnavailableProblem extends BaseProblem {
        public MediaReferenceUnavailableProblem(String detail) {
            super("urn:joutio:bff:media:referenceCheckUnavailable",
                    ProblemCategory.TECHNICAL,
                    "Media reference check unavailable",
                    detail,
                    Map.of());
        }
    }

    /** Clé de titre mal formée (`400`) : attendu {@code topicId:TIER}. */
    public static class InvalidEquippedTitleProblem extends BaseProblem {
        public InvalidEquippedTitleProblem(String titleKey) {
            super("urn:joutio:bff:profile:invalidTitle",
                    ProblemCategory.VALIDATION,
                    "Invalid title",
                    "The title key must be formatted as 'topicId:TIER'",
                    Map.of("titleKey", String.valueOf(titleKey)));
        }
    }

    /**
     * Titre non débloqué par le joueur (`400`) : le palier demandé n'est pas atteint dans ce sujet.
     * La progression ne se contourne pas côté client.
     */
    public static class TitleNotEarnedProblem extends BaseProblem {
        public TitleNotEarnedProblem(String titleKey, String reachedGrade) {
            super("urn:joutio:bff:profile:titleNotEarned",
                    ProblemCategory.VALIDATION,
                    "Title not earned",
                    "The title " + titleKey + " has not been unlocked (reached tier: " + reachedGrade + ")",
                    Map.of("titleKey", titleKey, "reachedGrade", reachedGrade));
        }
    }

    /** Un défi PENDING existe déjà entre ces deux joueurs sur ce sujet (409). */
    public static class ChallengeAlreadyPendingProblem extends BaseProblem {
        public ChallengeAlreadyPendingProblem(String topicId, String opponentId) {
            super("urn:joutio:bff:challenge:alreadyPending",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Challenge already pending",
                    "A challenge is already pending with this player on this topic",
                    Map.of("topicId", topicId, "opponentId", opponentId));
        }
    }

    /** Le joueur a atteint son quota de défis PENDING créés (429). */
    public static class ChallengeQuotaExceededProblem extends BaseProblem {
        public ChallengeQuotaExceededProblem(int limit) {
            super("urn:joutio:bff:challenge:quotaExceeded",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Challenge quota exceeded",
                    "The player has reached the maximum number of pending challenges (" + limit + ")",
                    Map.of("limit", limit));
        }
    }

    /** Un salon WAITING_PLAYERS existe déjà pour ce joueur sur ce sujet (409). */
    public static class RoomAlreadyOpenOnTopicProblem extends BaseProblem {
        public RoomAlreadyOpenOnTopicProblem(String topicId, String roomId) {
            super("urn:joutio:bff:room:alreadyOpenOnTopic",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Room already open on this topic",
                    "A room is already open for this player on this topic",
                    roomContext(topicId, roomId));
        }

        private static Map<String, Object> roomContext(String topicId, String roomId) {
            return roomId == null
                    ? Map.of("topicId", topicId)
                    : Map.of("topicId", topicId, "roomId", roomId);
        }
    }

    /** Le joueur a atteint son quota de salons WAITING_PLAYERS créés (429). */
    public static class RoomQuotaExceededProblem extends BaseProblem {
        public RoomQuotaExceededProblem(int limit) {
            super("urn:joutio:bff:room:quotaExceeded",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Room quota exceeded",
                    "The player has reached the maximum number of open rooms (" + limit + ")",
                    Map.of("limit", limit));
        }
    }
}
