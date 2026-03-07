import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;

public class TestDynamoConnection {

    public static void main(String[] args) {

        DynamoDbClient dynamoDb = DynamoDbClient.create();

        ListTablesResponse tables = dynamoDb.listTables();

        System.out.println("Tables:");

        tables.tableNames().forEach(System.out::println);

    }
}
