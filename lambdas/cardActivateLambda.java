package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class cardActivateLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final SqsClient sqsClient = SqsClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String cardTableName = System.getenv().getOrDefault("CARD_TABLE_NAME", "card-table");
    private final String notificationQueueUrl = System.getenv("NOTIFICATION_QUEUE_URL");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            String bodyString = (String) input.get("body");
            if (bodyString == null)
                return buildResponse(400, "{\"error\": \"Empty body\"}");

            Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
            String userId = (String) body.get("userId");
            String cardId = (String) body.get("cardId");

            if (cardId == null)
                return buildResponse(400, "{\"error\": \"cardId is required\"}");

            // 🔍 Buscar tarjeta por PK (uuid)
            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(cardTableName)
                    .keyConditionExpression("#uuid = :id")
                    .expressionAttributeNames(Map.of("#uuid", "uuid"))
                    .expressionAttributeValues(Map.of(":id", AttributeValue.builder().s(cardId).build()))
                    .build();

            QueryResponse queryResponse = dynamoDbClient.query(queryRequest);
            if (!queryResponse.hasItems() || queryResponse.items().isEmpty()) {
                return buildResponse(404, "{\"error\": \"Card not found\"}");
            }

            Map<String, AttributeValue> cardItem = queryResponse.items().get(0);
            String createdAt = cardItem.get("createdAt").s();

            // Actualizar a ACTIVATED
            UpdateItemRequest updateReq = UpdateItemRequest.builder()
                    .tableName(cardTableName)
                    .key(Map.of(
                            "uuid", AttributeValue.builder().s(cardId).build(),
                            "createdAt", AttributeValue.builder().s(createdAt).build()))
                    .updateExpression("SET #s = :val")
                    .expressionAttributeNames(Map.of("#s", "status"))
                    .expressionAttributeValues(Map.of(":val", AttributeValue.builder().s("ACTIVATED").build()))
                    .build();

            dynamoDbClient.updateItem(updateReq);

            if (notificationQueueUrl != null) {
                String payload = String.format(
                        "{\"type\": \"CARD.ACTIVATE\", \"data\": {\"userId\": \"%s\", \"cardId\": \"%s\"}}", userId,
                        cardId);
                sqsClient.sendMessage(
                        SendMessageRequest.builder().queueUrl(notificationQueueUrl).messageBody(payload).build());
            }

            return buildResponse(200, "{\"message\": \"Card activated successfully\"}");

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return buildResponse(500, "{\"error\": \"" + e.getMessage() + "\"}");
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
