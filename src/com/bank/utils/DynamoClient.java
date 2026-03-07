package com.bank.utils;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoClient {

    public static DynamoDbClient client = DynamoDbClient.create();

}
