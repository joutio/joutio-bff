package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.theme.domain.model.TopicTeamRole;

public record TopicTeamMemberView(
        String userId,
        String pseudonym,
        String avatarOptions,
        TopicTeamRole role,
        boolean premium) {}
