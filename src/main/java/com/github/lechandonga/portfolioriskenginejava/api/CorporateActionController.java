package com.github.lechandonga.portfolioriskenginejava.api;

import com.github.lechandonga.portfolioriskenginejava.corporate.CorporateActionResult;
import com.github.lechandonga.portfolioriskenginejava.corporate.CorporateActionService;
import com.github.lechandonga.portfolioriskenginejava.domain.CorporateAction;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts/{accountId}/corporate-actions")
public class CorporateActionController {

    private final CorporateActionService service;

    public CorporateActionController(CorporateActionService service) {
        this.service = service;
    }

    @PostMapping
    public CorporateActionResult apply(@PathVariable String accountId,
                                       @RequestBody CorporateActionDto dto) {
        return service.apply(accountId, new CorporateAction(
                dto.actionId(), dto.symbol(), dto.type(),
                dto.factor(), dto.cashPerShare()));
    }
}
