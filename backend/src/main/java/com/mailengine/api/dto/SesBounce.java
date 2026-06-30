package com.mailengine.api.dto;

import java.util.List;

public record SesBounce(String bounceType, List<SesRecipient> bouncedRecipients) {}
