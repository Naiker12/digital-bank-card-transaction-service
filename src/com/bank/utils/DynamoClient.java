package com.bank.utils;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoClient {

    public static DynamoDbClient client = DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .build();

}
