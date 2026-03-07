package com.bank.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.bank.service.CardService;

import java.util.Map;

public class CardPurchaseLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final CardService cardService = new CardService();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
            Map<String, String> queryParams = input.getQueryStringParameters();
            String cardId = queryParams != null ? queryParams.get("cardId") : null;
            String amountStr = queryParams != null ? queryParams.get("amount") : null;

            if (cardId == null || amountStr == null) {
                return response
                        .withStatusCode(400)
                        .withBody("{\"error\": \"cardId and amount are required\"}");
            }

            double amount = Double.parseDouble(amountStr);
            cardService.sendTransaction(cardId, amount);

            return response
                    .withStatusCode(200)
                    .withBody("{\"message\": \"Transaction sent to queue\"}")
                    .withHeaders(Map.of("Content-Type", "application/json"));

        } catch (Exception e) {
            return response
                    .withStatusCode(500)
                    .withBody("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
