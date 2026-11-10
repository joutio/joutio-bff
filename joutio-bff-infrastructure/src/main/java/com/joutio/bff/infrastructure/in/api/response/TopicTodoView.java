package com.joutio.bff.infrastructure.in.api.response;

/**
 * Ce qui attend l'équipe d'un sujet géré par le joueur (Mes sujets › À traiter) : questions à valider,
 * questions avec des signalements ouverts, questions suspendues. Un rédacteur n'en reçoit jamais.
 */
public record TopicTodoView(String topicId, int pending, int reportedQuestions, int suspended) {

    public int total() {
        return pending + reportedQuestions;
    }
}
