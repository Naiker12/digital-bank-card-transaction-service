package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;

/**
 * AWS Lambda function to handle failures in card requests.
 */
public class cardRequestFailedLambda implements RequestHandler<Map<String, Object>, String> {

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        // Lambda logic to handle and log a failed card request
        return "Failed request handled.";
    }
}
