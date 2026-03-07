package com.bank.service;

import com.bank.model.Card;
import com.bank.utils.DynamoClient;
import com.bank.utils.SQSClient;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CardService {

    private final String QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/195802271670/bank-card-transactions-queue";

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

        return card;
    }

    public void sendTransaction(String cardId, double amount) {

        String message = "{ \"card_id\": \"" + cardId + "\", \"amount\": " + amount + "}";

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .messageBody(message)
                .build();

        SQSClient.client.sendMessage(request);

    }

}
