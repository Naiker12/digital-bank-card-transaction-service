package model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Model class for Card entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class card {
    private String cardId;
    private String userId;
    private String cardNumber;
    private String status;
    private Double creditLimit;
    private Double currentBalance;
}
