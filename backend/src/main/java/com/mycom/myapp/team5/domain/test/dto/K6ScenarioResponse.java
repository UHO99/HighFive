package com.mycom.myapp.team5.domain.test.dto;

import java.util.List;

import com.mycom.myapp.team5.global.common.enums.K6Scenario;

public record K6ScenarioResponse(
        String id,
        String file,
        String name,
        String description,
        List<String> guides,
        String rampUp,
        String hold,
        String targetVus,
        boolean configurable,
        boolean advanced
) {
    public static K6ScenarioResponse from(K6Scenario scenario) {
        return new K6ScenarioResponse(
                scenario.getId(),
                scenario.getFile(),
                scenario.getScenarioName(),
                scenario.getDescription(),
                scenario.getGuides(),
                scenario.getRampUp(),
                scenario.getHold(),
                scenario.getTargetVus(),
                scenario.isConfigurable(),
                scenario.isAdvanced()
        );
    }
}
