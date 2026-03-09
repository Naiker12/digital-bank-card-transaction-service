package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class cardPurchaseLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final SqsClient sqsClient = SqsClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String cardTableName = System.getenv().getOrDefault("CARD_TABLE_NAME", "card-table");
    private final String transactionTableName = System.getenv().getOrDefault("TRANSACTION_TABLE_NAME",
            "transaction-table");
    private final String notificationQueueUrl = System.getenv("NOTIFICATION_QUEUE_URL");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        Map<String, Object> response = new HashMap<>();
        try {
            String bodyString = (String) input.get("body");
            Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);

            String merchant = (String) body.get("merchant");
            String cardId = (String) body.get("cardId");
            double amount = Double.parseDouble(body.get("amount").toString());

            // 1. Obtener la tarjeta de DynamoDB mediante Query (PK: uuid)
            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(cardTableName)
                    .keyConditionExpression("#pk = :id")
                    .expressionAttributeNames(Map.of("#pk", "uuid"))
                    .expressionAttributeValues(Map.of(":id", AttributeValue.builder().s(cardId).build()))
                    .build();

            QueryResponse queryResponse = dynamoDbClient.query(queryRequest);
            if (!queryResponse.hasItems() || queryResponse.items().isEmpty()) {
                return buildResponse(404, "{\"error\": \"Card not found\"}");
            }

            Map<String, AttributeValue> card = queryResponse.items().get(0);
            String cardType = card.containsKey("type") ? card.get("type").s() : "DEBIT";
            String createdAt = card.get("createdAt").s();
            String userId = card.containsKey("user_id") ? card.get("user_id").s()
                    : (card.containsKey("userId") ? card.get("userId").s() : "unknown");
            double balance = Double.parseDouble(card.get("balance").n());

            // 2. Validar Fondos
            if ("DEBIT".equalsIgnoreCase(cardType)) {
                if (balance < amount) {
                    return buildResponse(400, "{\"error\": \"Insufficient funds for debit card\"}");
                }
                balance -= amount;
            } else if ("CREDIT".equalsIgnoreCase(cardType)) {
                // Para crédito, asumimos que balance es crédito disponible
                if (balance < amount) {
                    return buildResponse(400, "{\"error\": \"Credit limit exceeded\"}");
                }
                balance -= amount;
            }

            // 3. Actualizar Balance en Tarjeta
            Map<String, AttributeValue> keyMap = new HashMap<>();
            keyMap.put("uuid", AttributeValue.builder().s(cardId).build());
            keyMap.put("createdAt", AttributeValue.builder().s(createdAt).build());

            UpdateItemRequest updateReq = UpdateItemRequest.builder()
                    .tableName(cardTableName)
                    .key(keyMap)
                    .updateExpression("SET balance = :b")
                    .expressionAttributeValues(
                            Map.of(":b", AttributeValue.builder().n(String.valueOf(balance)).build()))
                    .build();
            dynamoDbClient.updateItem(updateReq);

            // 4. Guardar Transacción
            String txUuid = UUID.randomUUID().toString();
            String txCreatedAt = Instant.now().toString();
            Map<String, AttributeValue> txValues = new HashMap<>();
            txValues.put("uuid", AttributeValue.builder().s(txUuid).build());
            txValues.put("createdAt", AttributeValue.builder().s(txCreatedAt).build());
            txValues.put("cardId", AttributeValue.builder().s(cardId).build());
            txValues.put("amount", AttributeValue.builder().n(String.valueOf(amount)).build());
            txValues.put("merchant", AttributeValue.builder().s(merchant).build());
            txValues.put("type", AttributeValue.builder().s("PURCHASE").build());

            dynamoDbClient.putItem(PutItemRequest.builder().tableName(transactionTableName).item(txValues).build());

            // 5. Enviar Notificación SQS
            if (notificationQueueUrl != null && !notificationQueueUrl.isEmpty()) {
                String payload = String.format(
                        "{\"type\":\"TRANSACTION.PURCHASE\",\"data\":{\"date\":\"%s\",\"merchant\":\"%s\",\"cardId\":\"%s\",\"amount\":%.2f,\"userId\":\"%s\"}}",
                        txCreatedAt, merchant, cardId, amount, userId);
                sqsClient.sendMessage(
                        SendMessageRequest.builder().queueUrl(notificationQueueUrl).messageBody(payload).build());
            }

            return buildResponse(200, "{\"message\": \"Purchase successful\", \"remainingBalance\": " + balance + "}");

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
