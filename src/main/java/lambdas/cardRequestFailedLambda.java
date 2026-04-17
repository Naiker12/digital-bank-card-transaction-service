package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;

/**
 * Función AWS Lambda para manejar fallos en las solicitudes de tarjetas.
 */
public class cardRequestFailedLambda implements RequestHandler<Map<String, Object>, String> {

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        // Lógica de Lambda para manejar y registrar una solicitud de tarjeta fallida
        return "Solicitud fallida manejada.";
    }
}
