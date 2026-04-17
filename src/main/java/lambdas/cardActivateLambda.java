package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class cardActivateLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

        private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
        private final SqsClient sqsClient = SqsClient.create();
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final String cardTableName = System.getenv().getOrDefault("CARD_TABLE_NAME", "card-table");
        private final String transactionTableName = System.getenv().getOrDefault("TRANSACTION_TABLE_NAME",
                        "transaction-table");
        private final String notificationQueueUrl = System.getenv("NOTIFICATION_QUEUE_URL");

        @Override
        public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
                try {
                        String bodyString = (String) input.get("body");
                        if (bodyString == null || bodyString.isEmpty()) {
                                return buildResponse(400, "{\"error\": \"El cuerpo de la solicitud está vacío\"}");
                        }

                        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
                        String userId = (String) body.get("userId");
                        String cardId = (String) body.get("cardId");

                        if (userId == null && cardId == null) {
                                return buildResponse(400,
                                                "{\"error\": \"Faltan datos requeridos (userId o cardId) para iniciar la activación.\"}");
                        }

                        Map<String, AttributeValue> cardToActivate = null;

                        if (cardId != null) {
                                // Buscar tarjeta específica por cardId
                                QueryRequest queryReq = QueryRequest.builder()
                                                .tableName(cardTableName)
                                                .keyConditionExpression("#u = :id")
                                                .expressionAttributeNames(Map.of("#u", "uuid"))
                                                .expressionAttributeValues(Map.of(":id",
                                                                AttributeValue.builder().s(cardId).build()))
                                                .build();
                                QueryResponse queryRes = dynamoDbClient.query(queryReq);
                                if (queryRes.hasItems() && !queryRes.items().isEmpty()) {
                                        cardToActivate = queryRes.items().get(0);
                                }
                        } else {
                                // Buscar tarjeta de crédito PENDIENTE para el usuario
                                ScanRequest scanReq = ScanRequest.builder()
                                                .tableName(cardTableName)
                                                .filterExpression("user_id = :uid AND #t = :type AND #s = :status")
                                                .expressionAttributeNames(Map.of("#t", "type", "#s", "status"))
                                                .expressionAttributeValues(Map.of(
                                                                ":uid", AttributeValue.builder().s(userId).build(),
                                                                ":type", AttributeValue.builder().s("CREDIT").build(),
                                                                ":status",
                                                                AttributeValue.builder().s("PENDING").build()))
                                                .build();
                                ScanResponse scanRes = dynamoDbClient.scan(scanReq);
                                if (scanRes.hasItems() && !scanRes.items().isEmpty()) {
                                        cardToActivate = scanRes.items().get(0);
                                }
                        }

                        if (cardToActivate == null) {
                                return buildResponse(404,
                                                "{\"error\": \"No se encontró una tarjeta de crédito pendiente para activar para este usuario.\"}");
                        }

                        String activeCardId = cardToActivate.get("uuid").s();
                        String activeUserId = cardToActivate.containsKey("user_id") ? cardToActivate.get("user_id").s()
                                        : userId;
                        String createdAt = cardToActivate.get("createdAt").s();

                        // Obtener tarjetas de débito del usuario para contar transacciones
                        ScanRequest debitCardsReq = ScanRequest.builder()
                                        .tableName(cardTableName)
                                        .filterExpression("user_id = :uid AND #t = :type")
                                        .expressionAttributeNames(Map.of("#t", "type"))
                                        .expressionAttributeValues(Map.of(
                                                        ":uid", AttributeValue.builder().s(activeUserId).build(),
                                                        ":type", AttributeValue.builder().s("DEBIT").build()))
                                        .build();
                        ScanResponse debitCardsRes = dynamoDbClient.scan(debitCardsReq);

                        List<String> debitCardIds = debitCardsRes.items().stream()
                                        .map(item -> item.get("uuid").s())
                                        .collect(Collectors.toList());

                        if (debitCardIds.isEmpty()) {
                                return buildResponse(400,
                                                "{\"error\": \"El usuario debe tener una tarjeta de débito con transacciones para activar la tarjeta de crédito\"}");
                        }

                        // Contar transacciones de tipo PURCHASE para esas tarjetas de débito
                        int totalPurchases = 0;
                        for (String dCardId : debitCardIds) {
                                ScanRequest txScan = ScanRequest.builder()
                                                .tableName(transactionTableName)
                                                .filterExpression("cardId = :cid AND #t = :type")
                                                .expressionAttributeNames(Map.of("#t", "type"))
                                                .expressionAttributeValues(Map.of(
                                                                ":cid", AttributeValue.builder().s(dCardId).build(),
                                                                ":type",
                                                                AttributeValue.builder().s("PURCHASE").build()))
                                                .build();
                                ScanResponse txRes = dynamoDbClient.scan(txScan);
                                totalPurchases += txRes.count();
                        }

                        if (totalPurchases < 10) {
                                return buildResponse(400,
                                                String.format(
                                                                "{\"error\": \"Para activar tu tarjeta de crédito, primero debes completar 10 compras con tu tarjeta de débito. Actualmente tienes %d compras.\"}",
                                                                totalPurchases));
                        }

                        // Actualizar estado a ACTIVATED
                        UpdateItemRequest updateReq = UpdateItemRequest.builder()
                                        .tableName(cardTableName)
                                        .key(Map.of(
                                                        "uuid", AttributeValue.builder().s(activeCardId).build(),
                                                        "createdAt", AttributeValue.builder().s(createdAt).build()))
                                        .updateExpression("SET #s = :val")
                                        .expressionAttributeNames(Map.of("#s", "status"))
                                        .expressionAttributeValues(
                                                        Map.of(":val", AttributeValue.builder().s("ACTIVATED").build()))
                                        .build();

                        dynamoDbClient.updateItem(updateReq);

                        if (notificationQueueUrl != null) {
                                String payload = String.format(
                                                "{\"type\": \"CARD.ACTIVATE\", \"data\": {\"userId\": \"%s\", \"cardId\": \"%s\"}}",
                                                activeUserId, activeCardId);
                                sqsClient.sendMessage(
                                                SendMessageRequest.builder().queueUrl(notificationQueueUrl)
                                                                .messageBody(payload).build());
                        }

                        return buildResponse(200,
                                        "{\"message\": \"Tarjeta activada exitosamente. Estado: ACTIVATED\", \"cardId\": \""
                                                        + activeCardId
                                                        + "\", \"status\": \"ACTIVATED\", \"totalDebitPurchases\": "
                                                        + totalPurchases + "}");

                } catch (Exception e) {
                        context.getLogger().log("Error fatal: " + e.toString());
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                        return buildResponse(500,
                                        "{\"error\": \"Error Interno del Servidor\", \"details\": \""
                                                        + errorMsg.replace("\"", "\\\"") + "\"}");
                }
        }

        private Map<String, Object> buildResponse(int statusCode, String body) {
                Map<String, Object> response = new HashMap<>();
                response.put("statusCode", statusCode);
                response.put("isBase64Encoded", false);
                response.put("headers", Map.of(
                                "Content-Type", "application/json",
                                "Access-Control-Allow-Origin", "*",
                                "Access-Control-Allow-Methods", "POST, OPTIONS"));
                response.put("body", body);
                return response;
        }
}
