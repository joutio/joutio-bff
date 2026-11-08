package com.joutio.bff.infrastructure.in.api.response;

import com.joutio.theme.domain.model.TopicPermissions;

public record TopicWorkshopView(TopicCardView topic, TopicPermissions permissions) {}
