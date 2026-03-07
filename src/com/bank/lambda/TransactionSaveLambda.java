package com.bank.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.bank.utils.DynamoClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TransactionSaveLambda implements RequestHandler<SQSEvent, String> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String handleRequest(SQSEvent event, Context context) {
        System.out.println("Processing SQS Event: " + event.getRecords().size() + " records.");

        for (SQSEvent.SQSMessage msg : event.getRecords()) {
            try {
                System.out.println("Message body: " + msg.getBody());
                JsonNode json = mapper.readTree(msg.getBody());

                String cardId = json.has("card_id") ? json.get("card_id").asText() : null;
                double amount = json.has("amount") ? json.get("amount").asDouble() : 0.0;

                if (cardId == null) {
                    System.err.println("Invalid message: card_id is missing.");
                    continue;
                }

                Map<String, AttributeValue> item = new HashMap<>();
                item.put("transaction_id", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
                item.put("card_id", AttributeValue.builder().s(cardId).build());
                item.put("amount", AttributeValue.builder().n(String.valueOf(amount)).build());
                item.put("status", AttributeValue.builder().s("SUCCESS").build());
                item.put("timestamp", AttributeValue.builder().s(java.time.Instant.now().toString()).build());

                PutItemRequest request = PutItemRequest.builder()
                        .tableName("bank-transactions")
                        .item(item)
                        .build();

                System.out.println("Attempting to save to DynamoDB: " + cardId);
                DynamoClient.client.putItem(request);
                System.out.println("Transaction saved successfully!");

            } catch (Exception e) {
                System.err.println("CRITICAL ERROR processing message: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return "Processed";
    }
}
