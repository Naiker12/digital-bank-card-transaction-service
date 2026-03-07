package com.bank.controller;

import com.bank.model.Card;
import com.bank.model.Transaction;
import com.bank.service.CardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/cards")
public class CardController {

    @Autowired
    private CardService cardService;

    @PostMapping("/request")
    public Card requestCard(@RequestParam String userId) {

        return cardService.createCard(userId);

    }

    @PostMapping("/purchase")
    public String purchase(
            @RequestParam String cardId,
            @RequestParam double amount) {

        cardService.sendTransaction(cardId, amount);

        return "Transaction sent to queue";

    }

    @GetMapping("/transactions")
    public List<Transaction> getTransactions() {

        return new ArrayList<>();

    }

}
