import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class TestSQS {

    public static void main(String[] args) {

        SqsClient sqs = SqsClient.create();

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl("https://sqs.us-east-1.amazonaws.com/195802271670/bank-card-transactions-queue")
                .messageBody("test transaction")
                .build();

        sqs.sendMessage(request);

        System.out.println("Message sent");

    }
}
