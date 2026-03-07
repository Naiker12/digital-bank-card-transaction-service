package com.bank.utils;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

public class SQSClient {

    public static SqsClient client = SqsClient.builder()
            .region(Region.US_EAST_1)
            .build();

}
