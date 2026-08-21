package com.mycom.myapp.team5.domain.test.dto;

import com.mycom.myapp.team5.global.common.enums.K6Scenario;

public record K6ScenarioResponse(
        String id,
        String file,
        String name,
        String description,
        String rampUp,
        String hold,
        String targetVus
) {
    public static K6ScenarioResponse from(K6Scenario scenario) {
        return new K6ScenarioResponse(
                scenario.getId(),
                scenario.getFile(),
                scenario.getScenarioName(),
                scenario.getDescription(),
                scenario.getRampUp(),
                scenario.getHold(),
                scenario.getTargetVus()
        );
    }
}
