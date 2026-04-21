package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class cardGetReportLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final SqsClient sqsClient = SqsClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String transactionTableName = System.getenv().getOrDefault("TRANSACTION_TABLE_NAME",
            "transaction-table");
    private final String cardTableName = System.getenv().getOrDefault("CARD_TABLE_NAME", "card-table");
    private final String notificationQueueUrl = System.getenv("NOTIFICATION_QUEUE_URL");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            Map<String, String> pathParameters = (Map<String, String>) input.get("pathParameters");
            String cardId = pathParameters != null ? pathParameters.get("card_id") : null;

            if (cardId == null || cardId.isEmpty()) {
                return buildResponse(400, "{\"error\": \"Se requiere el ID de la tarjeta en la URL\"}");
            }

            String start = null;
            String end = null;
            String bodyString = (String) input.get("body");
            if (bodyString != null && !bodyString.isEmpty()) {
                try {
                    Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
                    start = (String) body.get("start");
                    end = (String) body.get("end");
                } catch (Exception ignored) {
                    context.getLogger().log("No se pudieron parsear filtros opcionales del reporte");
                }
            }

            String keyConditionExpression = "cardId = :id";
            Map<String, AttributeValue> expressionValues = new HashMap<>();
            expressionValues.put(":id", AttributeValue.builder().s(cardId).build());

            if (start != null && end != null) {
                keyConditionExpression += " AND createdAt BETWEEN :start AND :end";
                expressionValues.put(":start", AttributeValue.builder().s(start).build());
                expressionValues.put(":end", AttributeValue.builder().s(end).build());
            }

            QueryRequest transactionsQuery = QueryRequest.builder()
                    .tableName(transactionTableName)
                    .indexName("CardIdIndex")
                    .keyConditionExpression(keyConditionExpression)
                    .expressionAttributeValues(expressionValues)
                    .build();

            QueryResponse transactionsResponse = dynamoDbClient.query(transactionsQuery);
            List<Map<String, AttributeValue>> transactions = transactionsResponse.items();

            List<Map<String, Object>> normalizedTransactions = new ArrayList<>();
            double totalExpenses = 0.0;
            double totalIncome = 0.0;

            for (Map<String, AttributeValue> item : transactions) {
                String type = item.containsKey("type") ? item.get("type").s() : "UNKNOWN";
                double amount = item.containsKey("amount") ? Double.parseDouble(item.get("amount").n()) : 0.0;
                String createdAtValue = item.containsKey("createdAt") ? item.get("createdAt").s() : "";
                String merchant = item.containsKey("merchant") ? item.get("merchant").s() : "Sin detalle";

                Map<String, Object> normalized = new HashMap<>();
                normalized.put("id", item.containsKey("uuid") ? item.get("uuid").s() : createdAtValue);
                normalized.put("cardId", item.containsKey("cardId") ? item.get("cardId").s() : cardId);
                normalized.put("type", type);
                normalized.put("merchant", merchant);
                normalized.put("amount", amount);
                normalized.put("createdAt", createdAtValue);
                normalizedTransactions.add(normalized);

                if ("PURCHASE".equalsIgnoreCase(type)) {
                    totalExpenses += amount;
                } else if ("PAYMENT_BALANCE".equalsIgnoreCase(type) || "SAVING".equalsIgnoreCase(type)) {
                    totalIncome += amount;
                }
            }

            normalizedTransactions.sort(
                    Comparator.comparing(
                            (Map<String, Object> tx) -> (String) tx.get("createdAt"),
                            Comparator.nullsLast(String::compareTo))
                            .reversed());

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

            if (notificationQueueUrl != null && !notificationQueueUrl.isEmpty()) {
                try {
                    String payload = String.format(
                            "{\"type\":\"REPORT.ACTIVITY\",\"data\":{\"userId\":\"%s\",\"cardId\":\"%s\",\"report\":\"Reporte generado correctamente\",\"count\":%d}}",
                            userId, cardId, normalizedTransactions.size());
                    sqsClient.sendMessage(
                            SendMessageRequest.builder().queueUrl(notificationQueueUrl).messageBody(payload).build());
                } catch (Exception notificationError) {
                    context.getLogger().log("No se pudo encolar la notificacion del reporte: "
                            + notificationError.getMessage());
                }
            }

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Reporte generado correctamente");
            responseBody.put("cardId", cardId);
            responseBody.put("transactions_count", normalizedTransactions.size());
            responseBody.put("transactions", normalizedTransactions);
            responseBody.put("summary", Map.of(
                    "expenses", totalExpenses,
                    "income", totalIncome));
            Map<String, Object> params = new HashMap<>();
            params.put("start", start);
            params.put("end", end);
            responseBody.put("params", params);

            return buildResponse(200, objectMapper.writeValueAsString(responseBody));

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
