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
import java.util.List;

public class cardGetReportLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final SqsClient sqsClient = SqsClient.create();
    private final String transactionTableName = System.getenv().getOrDefault("TRANSACTION_TABLE_NAME",
            "transaction-table");
    private final String cardTableName = System.getenv().getOrDefault("CARD_TABLE_NAME", "card-table");
    private final String notificationQueueUrl = System.getenv("NOTIFICATION_QUEUE_URL");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            Map<String, String> pathParameters = (Map<String, String>) input.get("pathParameters");
            String cardId = pathParameters != null ? pathParameters.get("card_id") : null;

            if (cardId == null) {
                return buildResponse(400, "{\"error\": \"Card ID is required in URL\"}");
            }

            // 1. Buscar transacciones de la tarjeta mediante Scan
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(transactionTableName)
                    .filterExpression("cardId = :id")
                    .expressionAttributeValues(Map.of(":id", AttributeValue.builder().s(cardId).build()))
                    .build();

            ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);
            List<Map<String, AttributeValue>> transactions = scanResponse.items();

            // 2. Buscar el userId para notificar
            QueryRequest cardQuery = QueryRequest.builder()
                    .tableName(cardTableName)
                    .keyConditionExpression("#pk = :id")
                    .expressionAttributeNames(Map.of("#pk", "uuid"))
                    .expressionAttributeValues(Map.of(":id", AttributeValue.builder().s(cardId).build()))
                    .build();

            QueryResponse cardResp = dynamoDbClient.query(cardQuery);
            String userId = "unknown";
            if (!cardResp.items().isEmpty()) {
                Map<String, AttributeValue> cardItem = cardResp.items().get(0);
                userId = cardItem.containsKey("user_id") ? cardItem.get("user_id").s()
                        : (cardItem.containsKey("userId") ? cardItem.get("userId").s() : "unknown");
            }

            // 3. Enviar notificación de reporte
            if (notificationQueueUrl != null) {
                String payload = String.format(
                        "{\"type\":\"REPORT.ACTIVITY\",\"data\":{\"userId\":\"%s\",\"cardId\":\"%s\",\"report\": \"%s\"}}",
                        userId, cardId, "Report generated correctly");
                sqsClient.sendMessage(
                        SendMessageRequest.builder().queueUrl(notificationQueueUrl).messageBody(payload).build());
            }

            return buildResponse(200, "{\"message\": \"Report generated and sent to email\", \"transactions_count\": "
                    + transactions.size() + "}");

        } catch (Exception e) {
            context.getLogger().log("Error en reporte: " + e.toString());
            return buildResponse(500, "{\"error\": \"Internal Server Error\", \"details\": \""
                    + e.toString().replace("\"", "\\\"") + "\"}");
        }
    }

    private Map<String, Object> buildResponse(int statusCode, String body) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("isBase64Encoded", false);
        response.put("headers", Map.of("Content-Type", "application/json", "Access-Control-Allow-Origin", "*"));
        response.put("body", body);
        return response;
    }
}
