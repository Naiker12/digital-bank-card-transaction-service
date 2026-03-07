package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;

/**
 * AWS Lambda function to handle payments on credit cards.
 */
public class cardPaidLambda implements RequestHandler<Map<String, Object>, String> {

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        // Lambda logic to record a credit card payment
        return "Payment registered successfully.";
    }
}
