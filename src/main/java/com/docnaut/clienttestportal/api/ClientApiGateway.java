package com.docnaut.clienttestportal.api;

import jakarta.servlet.http.HttpServletResponse;

/** The portal's HTTP-only boundary to the France Connector Client API. */
public interface ClientApiGateway {

    ApiExchange call(ClientApiClient.ApiRequest request);

    ApiExchange streamArtifact(ClientApiClient.ApiRequest request, HttpServletResponse target);
}
