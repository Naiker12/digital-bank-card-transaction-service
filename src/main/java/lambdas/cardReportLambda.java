package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;

/**
 * Función AWS Lambda para generar y recuperar reportes (vía S3).
 */
public class cardReportLambda implements RequestHandler<Map<String, Object>, String> {

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        // Lógica de Lambda para generar y cargar el reporte en S3
        return "Reporte generado exitosamente.";
    }
}
