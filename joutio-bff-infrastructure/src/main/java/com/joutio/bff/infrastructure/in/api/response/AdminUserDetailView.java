package com.joutio.bff.infrastructure.in.api.response;

import java.util.List;

/** Dossier de modération d'un joueur : compte, profil et signalements. */
public record AdminUserDetailView(
        AdminUserAccountView account,
        AdminUserProfileView profile,
        List<AdminUserReportView> reports
) {
}
