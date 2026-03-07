package com.bank.utils;

import software.amazon.awssdk.services.sqs.SqsClient;

public class SQSClient {

    public static SqsClient client = SqsClient.create();

}
