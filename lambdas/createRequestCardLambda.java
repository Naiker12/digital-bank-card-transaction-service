package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;

/**
 * AWS Lambda function to start a card request process.
 */
public class createRequestCardLambda implements RequestHandler<Map<String, Object>, String> {

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        // Lambda logic to create a new card request
        return "Card request initiated successfully.";
    }
}
