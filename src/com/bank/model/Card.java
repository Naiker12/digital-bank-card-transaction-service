package com.bank.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Card {

    private String cardId;
    private String userId;
    private String cardNumber;
    private double creditLimit;
    private double balance;
    private String status;

}
