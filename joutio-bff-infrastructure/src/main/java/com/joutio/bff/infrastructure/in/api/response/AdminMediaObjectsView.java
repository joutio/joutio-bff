package com.joutio.bff.infrastructure.in.api.response;

import java.util.List;

/**
 * Page d'objets du bucket. Pagination par curseur du stockage S3 ({@code nextCursor} absent en
 * fin de listing) — le tri suit l'ordre lexicographique des clés, imposé par S3.
 */
public record AdminMediaObjectsView(List<AdminMediaObjectView> objects, String nextCursor) {
}
