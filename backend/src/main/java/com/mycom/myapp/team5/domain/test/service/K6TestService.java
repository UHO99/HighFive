package com.mycom.myapp.team5.domain.test.service;

import com.mycom.myapp.team5.domain.test.dto.K6ScenarioResponse;
import com.mycom.myapp.team5.domain.test.dto.K6StatusResponse;

import java.util.List;

public interface K6TestService {

    List<K6ScenarioResponse> listScenarios();

    K6StatusResponse start(String scenarioId, long couponId);

    K6StatusResponse stop();

    K6StatusResponse status();

}
