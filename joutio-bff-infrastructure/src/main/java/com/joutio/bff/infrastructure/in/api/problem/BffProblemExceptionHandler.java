package com.joutio.bff.infrastructure.in.api.problem;

import com.joutio.microservice.core.domain.exception.BaseProblem;
import com.joutio.microservice.core.domain.exception.ProblemCategory;
import com.joutio.microservice.core.infrastructure.in.api.response.ExceptionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Mappe les {@link BaseProblem} émis par la façade (garde propriétaire, validation de liste) sur
 * le contrat d'erreur RFC 7807 du SDK. Les problèmes levés par les handlers Axon passent par le
 * handler du SDK ({@code Command/QueryExecutionException}) ; un problème levé dans un chaînage
 * asynchrone arrive enveloppé dans un {@code CompletionException}, dont Spring parcourt la chaîne
 * de causes lors de la résolution des {@code @ExceptionHandler}.
 */
@RestControllerAdvice
public class BffProblemExceptionHandler {

    @ExceptionHandler(BaseProblem.class)
    public ResponseEntity<ExceptionResponse> handle(BaseProblem problem, HttpServletRequest request) {
        return respond(problem, request, statusFor(problem.getCategory()));
    }

    /** Fichier refusé par le conteneur/Spring avant d'atteindre le contrôleur (limite multipart). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ExceptionResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception,
                                                                 HttpServletRequest request) {
        return respond(new BffProblems.MediaTooLargeProblem("fichier au-delà de la taille maximale autorisée"),
                request, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /** Fichier au-delà de la limite applicative média : 413 plutôt que 400. */
    @ExceptionHandler(BffProblems.MediaTooLargeProblem.class)
    public ResponseEntity<ExceptionResponse> handleMediaTooLarge(BffProblems.MediaTooLargeProblem problem,
                                                                 HttpServletRequest request) {
        return respond(problem, request, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /** Quota d'images de l'utilisateur atteint : 429 (réessayer après suppression). */
    @ExceptionHandler(BffProblems.MediaQuotaExceededProblem.class)
    public ResponseEntity<ExceptionResponse> handleMediaQuota(BffProblems.MediaQuotaExceededProblem problem,
                                                              HttpServletRequest request) {
        return respond(problem, request, HttpStatus.TOO_MANY_REQUESTS);
    }

    /** Stockage objet injoignable : 503 (dépendance externe, pas une faute du client). */
    @ExceptionHandler(BffProblems.MediaStorageUnavailableProblem.class)
    public ResponseEntity<ExceptionResponse> handleMediaStorage(BffProblems.MediaStorageUnavailableProblem problem,
                                                                HttpServletRequest request) {
        return respond(problem, request, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** Objet encore référencé : 409 (l'admin doit confirmer avec {@code force=true}). */
    @ExceptionHandler(BffProblems.MediaObjectReferencedProblem.class)
    public ResponseEntity<ExceptionResponse> handleMediaReferenced(BffProblems.MediaObjectReferencedProblem problem,
                                                                   HttpServletRequest request) {
        return respond(problem, request, HttpStatus.CONFLICT);
    }

    /** Compte déjà suspendu : 409. */
    @ExceptionHandler(BffProblems.UserAlreadySuspendedProblem.class)
    public ResponseEntity<ExceptionResponse> handleAlreadySuspended(BffProblems.UserAlreadySuspendedProblem problem,
                                                                    HttpServletRequest request) {
        return respond(problem, request, HttpStatus.CONFLICT);
    }

    /** Défi vers un joueur suspendu : 409. */
    @ExceptionHandler(BffProblems.ChallengeOpponentSuspendedProblem.class)
    public ResponseEntity<ExceptionResponse> handleOpponentSuspended(BffProblems.ChallengeOpponentSuspendedProblem problem,
                                                                     HttpServletRequest request) {
        return respond(problem, request, HttpStatus.CONFLICT);
    }

    /** Défi déjà en attente avec ce joueur sur ce sujet : 409. */
    @ExceptionHandler(BffProblems.ChallengeAlreadyPendingProblem.class)
    public ResponseEntity<ExceptionResponse> handleChallengeAlreadyPending(
            BffProblems.ChallengeAlreadyPendingProblem problem,
            HttpServletRequest request) {
        return respond(problem, request, HttpStatus.CONFLICT);
    }

    /** Salon déjà ouvert sur ce sujet : 409. */
    @ExceptionHandler(BffProblems.RoomAlreadyOpenOnTopicProblem.class)
    public ResponseEntity<ExceptionResponse> handleRoomAlreadyOpenOnTopic(
            BffProblems.RoomAlreadyOpenOnTopicProblem problem,
            HttpServletRequest request) {
        return respond(problem, request, HttpStatus.CONFLICT);
    }

    /** Quota de défis créés atteint : 429. */
    @ExceptionHandler(BffProblems.ChallengeQuotaExceededProblem.class)
    public ResponseEntity<ExceptionResponse> handleChallengeQuota(
            BffProblems.ChallengeQuotaExceededProblem problem,
            HttpServletRequest request) {
        return respond(problem, request, HttpStatus.TOO_MANY_REQUESTS);
    }

    /** Quota de salons créés atteint : 429. */
    @ExceptionHandler(BffProblems.RoomQuotaExceededProblem.class)
    public ResponseEntity<ExceptionResponse> handleRoomQuota(
            BffProblems.RoomQuotaExceededProblem problem,
            HttpServletRequest request) {
        return respond(problem, request, HttpStatus.TOO_MANY_REQUESTS);
    }

    /** Vérification des références indisponible : 503, la suppression est refusée (fail-closed). */
    @ExceptionHandler(BffProblems.MediaReferenceUnavailableProblem.class)
    public ResponseEntity<ExceptionResponse> handleMediaReferenceCheck(BffProblems.MediaReferenceUnavailableProblem problem,
                                                                       HttpServletRequest request) {
        return respond(problem, request, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static ResponseEntity<ExceptionResponse> respond(BaseProblem problem, HttpServletRequest request,
                                                             HttpStatus status) {
        ExceptionResponse response = new ExceptionResponse(
                problem.getType(),
                problem.getCategory(),
                problem.getTitle(),
                problem.getDetail(),
                problem.getContext(),
                status.value(),
                request.getRequestURI());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(response);
    }

    private static HttpStatus statusFor(ProblemCategory category) {
        return switch (category) {
            case BUSINESS_INVALID_COMMAND, VALIDATION -> HttpStatus.BAD_REQUEST;
            case PERMISSION -> HttpStatus.FORBIDDEN;
            case BUSINESS_RESOURCE_MISSING -> HttpStatus.NOT_FOUND;
            case BUSINESS_AGGREGATE, TECHNICAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
