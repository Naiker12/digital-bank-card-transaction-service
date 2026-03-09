package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class createRequestCardLambda implements RequestHandler<SQSEvent, String> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final SqsClient sqsClient = SqsClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String cardTableName = System.getenv().getOrDefault("CARD_TABLE_NAME", "card-table");
    private final String notificationQueueUrl = System.getenv("NOTIFICATION_QUEUE_URL");
    private final Random random = new Random();

    @Override
    public String handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                JsonNode body = objectMapper.readTree(message.getBody());
                String userId = body.has("userId") ? body.get("userId").asText() : "unknown";
                String requestType = body.has("request") ? body.get("request").asText()
                        : (body.has("type") ? body.get("type").asText() : "DEBIT");

                double score = random.nextInt(101); // 0-100
                double amount = 0;
                String status = "ACTIVATED";

                // 📐 Fórmula del requerimiento: amount = 100 + (score / 100) * (10000000 - 100)
                if ("CREDIT".equalsIgnoreCase(requestType)) {
                    amount = 100.0 + (score / 100.0) * (10000000.0 - 100.0);
                    status = "PENDING";
                } else {
                    amount = 500.0; // Saldo inicial cortesía para Débito
                }

                String cardUuid = UUID.randomUUID().toString();
                String createdAt = Instant.now().toString();

                Map<String, AttributeValue> itemValues = new HashMap<>();
                itemValues.put("uuid", AttributeValue.builder().s(cardUuid).build());
                itemValues.put("createdAt", AttributeValue.builder().s(createdAt).build());
                itemValues.put("user_id", AttributeValue.builder().s(userId).build());
                itemValues.put("type", AttributeValue.builder().s(requestType.toUpperCase()).build());
                itemValues.put("status", AttributeValue.builder().s(status).build());
                itemValues.put("balance", AttributeValue.builder().n(String.valueOf(amount)).build());

                dynamoDbClient.putItem(PutItemRequest.builder().tableName(cardTableName).item(itemValues).build());

                // 📧 Notificación con Contrato exacto
                if (notificationQueueUrl != null && !notificationQueueUrl.isEmpty()) {
                    String payload = String.format(
                            "{\"type\":\"CARD.CREATE\",\"data\":{\"userId\":\"%s\",\"date\":\"%s\",\"type\":\"%s\",\"amount\":%.2f,\"cardId\":\"%s\"}}",
                            userId, createdAt, requestType, amount, cardUuid);

                    sqsClient.sendMessage(
                            SendMessageRequest.builder().queueUrl(notificationQueueUrl).messageBody(payload).build());
                }

            } catch (Exception e) {
                context.getLogger().log("Error en creación de tarjeta: " + e.getMessage());
            }
        }
        return "SUCCESS";
    }
}
