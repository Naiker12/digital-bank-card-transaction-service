package com.bank.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.bank.model.Card;
import com.bank.service.CardService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class CreateRequestCardLambda
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final CardService cardService = new CardService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
            Map<String, String> queryParams = input.getQueryStringParameters();
            String userId = queryParams != null ? queryParams.get("userId") : null;

            if (userId == null) {
                return response
                        .withStatusCode(400)
                        .withBody("{\"error\": \"userId is required\"}");
            }

            Card card = cardService.createCard(userId);
            String body = mapper.writeValueAsString(card);

            return response
                    .withStatusCode(201)
                    .withBody(body)
                    .withHeaders(Map.of("Content-Type", "application/json"));

        } catch (Exception e) {
            return response
                    .withStatusCode(500)
                    .withBody("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
