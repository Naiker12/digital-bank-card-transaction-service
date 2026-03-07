package model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * Model class for Transaction entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class transaction {
    private String transactionId;
    private String cardId;
    private Double amount;
    private String type; // PURCHASE, PAYMENT, etc.
    private String description;
    private LocalDateTime timestamp;
    private String status;
}
