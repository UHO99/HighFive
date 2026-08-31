package com.mycom.myapp.team5.domain.test.service;

import java.util.List;

import com.mycom.myapp.team5.domain.test.dto.K6RunRequest;
import com.mycom.myapp.team5.domain.test.dto.K6ScenarioResponse;
import com.mycom.myapp.team5.domain.test.dto.K6StatusResponse;
import com.mycom.myapp.team5.domain.test.dto.K6SummaryResponse;

public interface K6TestService {

	List<K6ScenarioResponse> listScenarios();

	K6StatusResponse start(K6RunRequest request);

	K6StatusResponse stop();

	K6StatusResponse status();

	K6SummaryResponse summary();

}
