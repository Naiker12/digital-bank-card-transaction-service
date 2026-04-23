package lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class catalogLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final S3Client s3Client = S3Client.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String bucketName = System.getenv("CATALOG_BUCKET_NAME");
    private final String fileKey = System.getenv().getOrDefault("CATALOG_FILE_KEY", "servicios.csv");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String method = input != null ? (String) input.get("httpMethod") : null;

        try {
            if ("GET".equalsIgnoreCase(method)) {
                return getCatalogHandler();
            }
            if ("POST".equalsIgnoreCase(method)) {
                return updateCatalogHandler(input);
            }
            return buildResponse(405, Map.of("error", "Method " + method + " not allowed"));
        } catch (Exception e) {
            context.getLogger().log("Catalog system error: " + e.getMessage());
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> getCatalogHandler() {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            ResponseInputStream<?> object = s3Client.getObject(request);
            String content = new String(object.readAllBytes(), StandardCharsets.UTF_8);
            List<Map<String, Object>> catalog = parseCsv(content);
            return buildResponse(200, catalog);
        } catch (S3Exception e) {
            return buildResponse(404, Map.of("error", "Catalog file not found"));
        } catch (Exception e) {
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> updateCatalogHandler(Map<String, Object> input) {
        try {
            String body = input.get("body") != null ? input.get("body").toString() : "[]";
            String csvContent = normalizeCatalogInput(body);
            if (csvContent == null || csvContent.isBlank()) {
                return buildResponse(400, Map.of("error", "Catalog data is required"));
            }

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType("text/csv")
                    .build();
            s3Client.putObject(request, RequestBody.fromString(csvContent));

            return buildResponse(200, Map.of("message", "Catalog updated successfully"));
        } catch (Exception e) {
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    private List<Map<String, Object>> parseCsv(String content) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                return rows;
            }

            List<String> headers = parseCsvLine(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String header = headers.get(i);
                    String value = i < values.size() ? values.get(i) : "";
                    row.putAll(mapCatalogHeader(header, value));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private String normalizeCatalogInput(String body) throws Exception {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception parseError) {
            return body.trim();
        }

        if (root == null || root.isNull()) {
            return "";
        }

        if (root.isObject() && root.hasNonNull("csv_data")) {
            return root.get("csv_data").asText("");
        }

        if (root.isTextual()) {
            return root.asText("");
        }

        if (!root.isArray()) {
            return "";
        }

        List<Map<String, Object>> catalog = objectMapper.readValue(
                root.toString(),
                new TypeReference<List<Map<String, Object>>>() {
                });

        if (catalog == null || catalog.isEmpty()) {
            return "";
        }

        List<String> headers = List.of(
                "ID",
                "Categoria",
                "Proveedor",
                "Servicio",
                "Plan",
                "Precio Mensual (US$)",
                "Velocidad/Detalles",
                "Estado");
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", headers)).append('\n');

        for (Map<String, Object> row : catalog) {
            List<String> values = new ArrayList<>();
            for (String header : headers) {
                values.add(escapeCsvCell(readCatalogValue(row, header)));
            }
            csv.append(String.join(",", values)).append('\n');
        }

        return csv.toString();
    }

    private String readCatalogValue(Map<String, Object> row, String header) {
        return switch (header) {
            case "ID" -> safeText(row.getOrDefault("id", row.getOrDefault("ID", "")));
            case "Categoria" -> safeText(row.getOrDefault("categoria", row.getOrDefault("Categoria", "")));
            case "Proveedor" -> safeText(row.getOrDefault("proveedor", row.getOrDefault("Proveedor", "")));
            case "Servicio" -> safeText(row.getOrDefault("servicio", row.getOrDefault("Servicio", "")));
            case "Plan" -> safeText(row.getOrDefault("plan", row.getOrDefault("Plan", "")));
            case "Precio Mensual (US$)" -> safeText(
                    row.getOrDefault("precio_mensual", row.getOrDefault("Precio Mensual (US$)", "")));
            case "Velocidad/Detalles" -> safeText(
                    row.getOrDefault("detalles", row.getOrDefault("Velocidad/Detalles", "")));
            case "Estado" -> safeText(row.getOrDefault("estado", row.getOrDefault("Estado", "")));
            default -> safeText(row.getOrDefault(header, ""));
        };
    }

    private Map<String, Object> mapCatalogHeader(String header, String value) {
        Map<String, Object> row = new LinkedHashMap<>();
        switch (header) {
            case "ID" -> row.put("id", value);
            case "Categoria" -> row.put("categoria", value);
            case "Proveedor" -> row.put("proveedor", value);
            case "Servicio" -> row.put("servicio", value);
            case "Plan" -> row.put("plan", value);
            case "Precio Mensual (US$)" -> row.put("precio_mensual", value);
            case "Velocidad/Detalles" -> row.put("detalles", value);
            case "Estado" -> row.put("estado", value);
            default -> row.put(header, value);
        }
        return row;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private String escapeCsvCell(Object value) {
        String text = safeText(value);
        boolean mustQuote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (mustQuote) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private String safeText(Object value) {
        return value == null ? "" : value.toString();
    }

    private Map<String, Object> buildResponse(int statusCode, Object body) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("statusCode", statusCode);
        response.put("headers", Map.of(
                "Content-Type", "application/json",
                "Access-Control-Allow-Origin", "*"));
        try {
            response.put("body", objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            response.put("body", "{\"error\":\"Unable to serialize response\"}");
        }
        response.put("isBase64Encoded", false);
        return response;
    }
}
