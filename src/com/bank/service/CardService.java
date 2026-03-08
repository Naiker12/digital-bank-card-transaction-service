package com.bank.service;

import com.bank.model.Card;
import com.bank.utils.DynamoClient;
import com.bank.utils.SQSClient;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CardService {

        private final String QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/195802271670/bank-card-transactions-queue";
        private final String NOTIFICATION_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/195802271670/bank-notification-queue";

        public Card createCard(String userId) {

                Card card = Card.builder()
                                .cardId(UUID.randomUUID().toString())
                                .userId(userId)
                                .cardNumber("4000-1234-5678-9999")
                                .creditLimit(1000)
                                .balance(1000)
                                .status("PENDING")
                                .build();

                Map<String, AttributeValue> item = new HashMap<>();

                item.put("card_id", AttributeValue.builder().s(card.getCardId()).build());
                item.put("user_id", AttributeValue.builder().s(card.getUserId()).build());
                item.put("card_number", AttributeValue.builder().s(card.getCardNumber()).build());
                item.put("status", AttributeValue.builder().s(card.getStatus()).build());

                PutItemRequest request = PutItemRequest.builder()
                                .tableName("bank-cards")
                                .item(item)
                                .build();

                DynamoClient.client.putItem(request);

                // Send notification for card creation
                sendNotificationEvent("CARD_CREATED", userId, card.getCardId(), "naikergomez0123@gmail.com", 0);

                return card;
        }

        public void sendTransaction(String cardId, double amount) {

                String message = "{ \"card_id\": \"" + cardId + "\", \"amount\": " + amount + "}";

                SendMessageRequest request = SendMessageRequest.builder()
                                .queueUrl(QUEUE_URL)
                                .messageBody(message)
                                .build();

                SQSClient.client.sendMessage(request);

                // Send notification for purchase
                sendNotificationEvent("CARD_PURCHASE", "Naiker Gomez", cardId, "naikergomez0123@gmail.com", amount);
        }

        private void sendNotificationEvent(String eventType, String userId, String cardId, String email,
                        double amount) {
                String message = String.format(java.util.Locale.US,
                                "{\"eventType\": \"%s\", \"userId\": \"%s\", \"cardId\": \"%s\", \"email\": \"%s\", \"amount\": %.2f, \"timestamp\": \"%s\"}",
                                eventType, userId, cardId, email, amount, java.time.Instant.now().toString());

                SendMessageRequest request = SendMessageRequest.builder()
                                .queueUrl(NOTIFICATION_QUEUE_URL)
                                .messageBody(message)
                                .build();

                SQSClient.client.sendMessage(request);
        }

        public List<Map<String, AttributeValue>> getTransactionsByCard(String cardId) {
                Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
                expressionAttributeValues.put(":v1", AttributeValue.builder().s(cardId).build());

                ScanRequest scanRequest = ScanRequest.builder()
                                .tableName("bank-transactions")
                                .filterExpression("card_id = :v1")
                                .expressionAttributeValues(expressionAttributeValues)
                                .build();

                return DynamoClient.client.scan(scanRequest).items();
        }

}
