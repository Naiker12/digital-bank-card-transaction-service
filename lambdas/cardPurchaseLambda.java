package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;

/**
 * AWS Lambda function to process a card purchase.
 */
public class cardPurchaseLambda implements RequestHandler<Map<String, Object>, String> {

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        // Lambda logic for purchase validation and processing
        return "Purchase processed successfully.";
    }
}
