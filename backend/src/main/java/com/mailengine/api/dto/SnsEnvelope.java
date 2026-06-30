package com.mailengine.api.dto;

public record SnsEnvelope(String Type, String SubscribeURL, String Message) {}
