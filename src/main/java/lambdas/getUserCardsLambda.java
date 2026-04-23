package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class getUserCardsLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String cardTableName = System.getenv().getOrDefault("CARD_TABLE_NAME", "card-table");
    private final String transactionTableName = System.getenv().getOrDefault("TRANSACTION_TABLE_NAME",
            "transaction-table");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            Map<String, String> pathParameters = (Map<String, String>) input.get("pathParameters");
            String userId = pathParameters != null ? pathParameters.get("user_id") : null;

            if (userId == null || userId.isEmpty()) {
                return buildResponse(400, "{\"error\": \"Se requiere el user_id en la ruta\"}");
            }

            context.getLogger().log("Buscando tarjetas para el usuario: " + userId);

            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(cardTableName)
                    .indexName("UserIdIndex")
                    .keyConditionExpression("user_id = :uid")
                    .expressionAttributeValues(Map.of(":uid", AttributeValue.builder().s(userId).build()))
                    .build();

            QueryResponse queryResponse = dynamoDbClient.query(queryRequest);
            List<Map<String, Object>> cardsList = new ArrayList<>();
            int totalDebitPurchases = 0;
            List<Map<String, Object>> pendingCreditCards = new ArrayList<>();

            for (Map<String, AttributeValue> item : queryResponse.items()) {
                Map<String, Object> cardMap = new HashMap<>();
                String cardId = item.get("uuid").s();
                String cardType = item.containsKey("type") ? item.get("type").s() : "DEBIT";
                String status = item.containsKey("status") ? item.get("status").s() : "ACTIVE";
                String createdAt = item.containsKey("createdAt") ? item.get("createdAt").s() : "";
                int purchaseCount = resolvePurchaseCount(item, cardType);

                cardMap.put("uuid", cardId);
                cardMap.put("user_id", item.containsKey("user_id") ? item.get("user_id").s() : userId);
                cardMap.put("cardNumber", item.containsKey("cardNumber") ? item.get("cardNumber").s() : "****");
                cardMap.put("balance", item.containsKey("balance") ? Double.parseDouble(item.get("balance").n()) : 0.0);
                cardMap.put("type", cardType);
                cardMap.put("status", status);
                cardMap.put("creditLimit", item.containsKey("creditLimit") ? Double.parseDouble(item.get("creditLimit").n()) : null);
                cardMap.put("expiry", item.containsKey("expiry") ? item.get("expiry").s() : "12/28");
                cardMap.put("createdAt", createdAt);
                cardMap.put("purchaseCount", purchaseCount);

                cardsList.add(cardMap);

                if ("DEBIT".equalsIgnoreCase(cardType)) {
                    totalDebitPurchases += purchaseCount;
                } else if ("CREDIT".equalsIgnoreCase(cardType) && "PENDING".equalsIgnoreCase(status)) {
                    pendingCreditCards.add(cardMap);
                }
            }

            if (totalDebitPurchases >= 10 && !pendingCreditCards.isEmpty()) {
                activateEligibleCreditCards(pendingCreditCards, userId, context);
            }

            String jsonResponse = objectMapper.writeValueAsString(cardsList);
            return buildResponse(200, jsonResponse);

        } catch (Exception e) {
            context.getLogger().log("Error al obtener tarjetas del usuario: " + e.toString());
            return buildResponse(500, "{\"error\": \"Error interno del servidor\", \"details\": \"" + e.getMessage() + "\"}");
        }
    }

    private int resolvePurchaseCount(Map<String, AttributeValue> cardItem, String cardType) {
        if (cardItem.containsKey("purchaseCount")) {
            try {
                return Integer.parseInt(cardItem.get("purchaseCount").n());
            } catch (Exception ignored) {
                return 0;
            }
        }

        if (!"DEBIT".equalsIgnoreCase(cardType)) {
            return 0;
        }

        try {
            QueryRequest txQuery = QueryRequest.builder()
                    .tableName(transactionTableName)
                    .indexName("CardIdIndex")
                    .keyConditionExpression("cardId = :id")
                    .expressionAttributeValues(Map.of(":id", AttributeValue.builder().s(cardItem.get("uuid").s()).build()))
                    .select(Select.COUNT)
                    .build();

            return dynamoDbClient.query(txQuery).count();
        } catch (Exception e) {
            return 0;
        }
    }

    private void activateEligibleCreditCards(List<Map<String, Object>> pendingCreditCards, String userId, Context context) {
        for (Map<String, Object> card : pendingCreditCards) {
            try {
                String creditCardId = (String) card.get("uuid");
                String createdAt = (String) card.get("createdAt");

                Map<String, AttributeValue> keyMap = new HashMap<>();
                keyMap.put("uuid", AttributeValue.builder().s(creditCardId).build());
                keyMap.put("createdAt", AttributeValue.builder().s(createdAt).build());

                UpdateItemRequest activateReq = UpdateItemRequest.builder()
                        .tableName(cardTableName)
                        .key(keyMap)
                        .updateExpression("SET #s = :a")
                        .expressionAttributeNames(Map.of("#s", "status"))
                        .expressionAttributeValues(
                                Map.of(":a", AttributeValue.builder().s("ACTIVATED").build()))
                        .build();

                dynamoDbClient.updateItem(activateReq);
                card.put("status", "ACTIVATED");
                context.getLogger().log("Tarjeta de crédito " + creditCardId + " activada para el usuario " + userId
                        + " después de alcanzar 10 compras con débito.");
            } catch (Exception e) {
                context.getLogger().log("No se pudo activar una tarjeta pendiente para el usuario " + userId
                        + ": " + e.getMessage());
            }
        }
    }

    private Map<String, Object> buildResponse(int statusCode, String body) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("isBase64Encoded", false);
        response.put("headers", Map.of(
                "Content-Type", "application/json",
                "Access-Control-Allow-Origin", "*",
                "Access-Control-Allow-Methods", "GET, OPTIONS"));
        response.put("body", body);
        return response;
    }
}
