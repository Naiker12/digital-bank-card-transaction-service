package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class getUserCardsLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String cardTableName = System.getenv().getOrDefault("CARD_TABLE_NAME", "card-table");

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

            for (Map<String, AttributeValue> item : queryResponse.items()) {
                Map<String, Object> cardMap = new HashMap<>();

                cardMap.put("uuid", item.get("uuid").s());
                cardMap.put("user_id", item.containsKey("user_id") ? item.get("user_id").s() : userId);
                cardMap.put("cardNumber", item.containsKey("cardNumber") ? item.get("cardNumber").s() : "****");
                cardMap.put("balance", item.containsKey("balance") ? Double.parseDouble(item.get("balance").n()) : 0.0);
                cardMap.put("type", item.containsKey("type") ? item.get("type").s() : "DEBIT");
                cardMap.put("status", item.containsKey("status") ? item.get("status").s() : "ACTIVE");
                cardMap.put("creditLimit", item.containsKey("creditLimit") ? Double.parseDouble(item.get("creditLimit").n()) : null);
                cardMap.put("expiry", item.containsKey("expiry") ? item.get("expiry").s() : "12/28");
                cardsList.add(cardMap);
            }

            String jsonResponse = objectMapper.writeValueAsString(cardsList);

            return buildResponse(200, jsonResponse);

        } catch (Exception e) {
            context.getLogger().log("Error al obtener tarjetas del usuario: " + e.toString());
            return buildResponse(500, "{\"error\": \"Error interno del servidor\", \"details\": \"" + e.getMessage() + "\"}");
        }
    }

    private Map<String, Object> buildResponse(int statusCode, String body) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("isBase64Encoded", false);
        response.put("headers", Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*",
            "Access-Control-Allow-Methods", "GET, OPTIONS"
        ));
        response.put("body", body);
        return response;
    }
}
