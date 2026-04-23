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

                Map<String, Object> body = objectMapper.readValue(message.getBody(), Map.class);
                String userId = (String) body.get("userId");
                String requestType = (String) body.get("request");

                context.getLogger().log("Procesando solicitud de tarjeta: " + requestType + " para el usuario: " + userId);

                if (userId == null || requestType == null) {
                    context.getLogger().log("Mensaje inválido: userId o request es nulo");
                    continue;
                }

                if (isAlreadyCreated(userId, requestType, context)) {
                    context.getLogger()
                            .log("La tarjeta de tipo " + requestType + " ya existe para el usuario: " + userId + ". Omitiendo.");
                    continue;
                }

                if ("CREDIT".equalsIgnoreCase(requestType)) {
                    createCard(userId, "CREDIT", 0.0, "PENDING", context);
                } else if ("DEBIT".equalsIgnoreCase(requestType)) {
                    createCard(userId, "DEBIT", 500.0, "ACTIVATED", context);
                }

            } catch (Exception e) {
                context.getLogger().log("Error al procesar el mensaje SQS: " + e.getMessage());
            }
        }
        return null;
    }

    private boolean isAlreadyCreated(String userId, String type, Context context) {
        try {

            software.amazon.awssdk.services.dynamodb.model.QueryRequest queryRequest = software.amazon.awssdk.services.dynamodb.model.QueryRequest
                    .builder()
                    .tableName(cardTableName)
                    .indexName("UserIdIndex")
                    .keyConditionExpression("user_id = :u")
                    .expressionAttributeValues(Map.of(":u", AttributeValue.builder().s(userId).build()))
                    .build();

            software.amazon.awssdk.services.dynamodb.model.QueryResponse response = dynamoDbClient.query(queryRequest);

            for (Map<String, AttributeValue> item : response.items()) {
                if (item.containsKey("type") && item.get("type").s().equalsIgnoreCase(type)) {
                    return true;
                }
            }
        } catch (Exception e) {
            context.getLogger().log("Error al verificar tarjeta existente: " + e.getMessage());
        }
        return false;
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
            item.put("purchaseCount", AttributeValue.builder().n("0").build());

            dynamoDbClient.putItem(PutItemRequest.builder().tableName(cardTableName).item(item).build());

            if (notificationQueueUrl != null && !notificationQueueUrl.isEmpty()) {
                String payload = String.format(
                        "{\"type\":\"CARD.CREATE\",\"data\":{\"userId\":\"%s\",\"cardId\":\"%s\",\"type\":\"%s\",\"status\":\"%s\"}}",
                        userId, uuid, type, status);
                sqsClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(notificationQueueUrl)
                        .messageBody(payload).build());
            }

            context.getLogger().log("Tarjeta creada: " + uuid + " para el usuario: " + userId + " tipo: " + type);

        } catch (Exception e) {
            context.getLogger().log("Error al crear la tarjeta: " + e.getMessage());
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
