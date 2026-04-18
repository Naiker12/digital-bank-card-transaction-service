package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Lambda para obtener la información de una tarjeta por su ID.
 * Utilizada por el Payment Service para validar tarjetas y consultar saldos.
 * Ruta: GET /card/info/{card_id}
 */
public class cardInfoLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final String cardTableName = System.getenv().getOrDefault("CARD_TABLE_NAME", "card-table");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            Map<String, String> pathParameters = (Map<String, String>) input.get("pathParameters");
            String cardId = pathParameters != null ? pathParameters.get("card_id") : null;

            if (cardId == null || cardId.isEmpty()) {
                return buildResponse(400, "{\"error\": \"Se requiere el ID de la tarjeta en la URL\"}");
            }

            // Consultar tarjeta por uuid (partition key)
            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(cardTableName)
                    .keyConditionExpression("#pk = :id")
                    .expressionAttributeNames(Map.of("#pk", "uuid"))
                    .expressionAttributeValues(Map.of(":id", AttributeValue.builder().s(cardId).build()))
                    .build();

            QueryResponse queryResponse = dynamoDbClient.query(queryRequest);

            if (!queryResponse.hasItems() || queryResponse.items().isEmpty()) {
                return buildResponse(404, "{\"error\": \"Tarjeta no encontrada\"}");
            }

            Map<String, AttributeValue> card = queryResponse.items().get(0);

            // Construir respuesta con los datos de la tarjeta
            String userId = card.containsKey("user_id") ? card.get("user_id").s()
                    : (card.containsKey("userId") ? card.get("userId").s() : "unknown");
            String balance = card.containsKey("balance") ? card.get("balance").n() : "0";
            String type = card.containsKey("type") ? card.get("type").s() : "DEBIT";
            String status = card.containsKey("status") ? card.get("status").s() : "UNKNOWN";
            String cardNumber = card.containsKey("cardNumber") ? card.get("cardNumber").s() : "";

            String responseBody = String.format(
                    "{\"uuid\":\"%s\",\"userId\":\"%s\",\"balance\":%s,\"type\":\"%s\",\"status\":\"%s\",\"cardNumber\":\"%s\"}",
                    cardId, userId, balance, type, status, cardNumber);

            return buildResponse(200, responseBody);

        } catch (Exception e) {
            context.getLogger().log("Error al obtener info de tarjeta: " + e.getMessage());
            return buildResponse(500, "{\"error\": \"Error interno del servidor\"}");
        }
    }

    private Map<String, Object> buildResponse(int statusCode, String body) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("headers", Map.of("Content-Type", "application/json", "Access-Control-Allow-Origin", "*"));
        response.put("body", body);
        return response;
    }
}
