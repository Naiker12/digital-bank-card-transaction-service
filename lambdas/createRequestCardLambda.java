package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class createRequestCardLambda implements RequestHandler<SQSEvent, Void> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final SqsClient sqsClient = SqsClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String cardTableName = System.getenv().getOrDefault("CARD_TABLE_NAME", "card-table");
    private final String notificationQueueUrl = System.getenv("NOTIFICATION_QUEUE_URL");

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                // 1. Read SQS message data (consistent with register_user.py)
                Map<String, Object> body = objectMapper.readValue(message.getBody(), Map.class);
                String userId = (String) body.get("userId");
                String requestType = (String) body.get("request"); // "DEBIT" or "CREDIT"

                context.getLogger().log("Processing card request: " + requestType + " for user: " + userId);

                if (userId == null || requestType == null) {
                    context.getLogger().log("Invalid message: userId or request is null");
                    continue;
                }

                // 2. Process specific request
                if ("CREDIT".equalsIgnoreCase(requestType)) {
                    // Credit card starts PENDING with 5000 limit
                    createCard(userId, "CREDIT", 5000.0, "PENDING", context);
                } else if ("DEBIT".equalsIgnoreCase(requestType)) {
                    // Debit card starts ACTIVATED with 0 balance
                    createCard(userId, "DEBIT", 0.0, "ACTIVATED", context);
                }

            } catch (Exception e) {
                context.getLogger().log("Error processing SQS message: " + e.getMessage());
            }
        }
        return null;
    }

    private void createCard(String userId, String type, double balance, String status, Context context) {
        try {
            String uuid = UUID.randomUUID().toString();
            String createdAt = Instant.now().toString();
            String cardNumber = generateCardNumber();

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("uuid", AttributeValue.builder().s(uuid).build());
            item.put("createdAt", AttributeValue.builder().s(createdAt).build());
            item.put("user_id", AttributeValue.builder().s(userId).build());
            item.put("type", AttributeValue.builder().s(type).build());
            item.put("balance", AttributeValue.builder().n(String.valueOf(balance)).build());
            item.put("status", AttributeValue.builder().s(status).build());
            item.put("cardNumber", AttributeValue.builder().s(cardNumber).build());

            dynamoDbClient.putItem(PutItemRequest.builder().tableName(cardTableName).item(item).build());

            // 4. Send Notification
            if (notificationQueueUrl != null && !notificationQueueUrl.isEmpty()) {
                String payload = String.format(
                        "{\"type\":\"CARD.CREATE\",\"data\":{\"userId\":\"%s\",\"cardId\":\"%s\",\"type\":\"%s\",\"status\":\"%s\"}}",
                        userId, uuid, type, status);
                sqsClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(notificationQueueUrl)
                        .messageBody(payload).build());
            }

            context.getLogger().log("Card created: " + uuid + " for user: " + userId + " type: " + type);

        } catch (Exception e) {
            context.getLogger().log("Error creating card: " + e.getMessage());
        }
    }

    private String generateCardNumber() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            int n = random.nextInt(9000) + 1000;
            sb.append(n);
            if (i < 3)
                sb.append("-");
        }
        return sb.toString();
    }
}
