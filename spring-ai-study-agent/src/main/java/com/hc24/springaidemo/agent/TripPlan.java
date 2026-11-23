package com.hc24.springaidemo.agent;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Structured itinerary returned by {@link TripPlanAgent}.
 */
public record TripPlan(
        @JsonPropertyDescription("城市名称") String city,
        @JsonPropertyDescription("建议游玩天数") int days,
        @JsonPropertyDescription("天气概况") String weather,
        @JsonPropertyDescription("住处建议") String stay,
        @JsonPropertyDescription("餐饮建议") String food,
        @JsonPropertyDescription("按天排列的行程要点") List<String> highlights,
        @JsonPropertyDescription("每项信息来自工具、资料还是常识") String sources) {
}
