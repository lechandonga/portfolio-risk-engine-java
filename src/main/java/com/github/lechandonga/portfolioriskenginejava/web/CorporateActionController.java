package com.github.lechandonga.portfolioriskenginejava.web;

import com.github.lechandonga.portfolioriskenginejava.domain.CorporateAction;
import com.github.lechandonga.portfolioriskenginejava.dto.Requests.CorporateActionRequest;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.CorporateActionView;
import com.github.lechandonga.portfolioriskenginejava.service.CorporateActionRepository;
import com.github.lechandonga.portfolioriskenginejava.service.CorporateActionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CorporateActionController {
    private final CorporateActionService service;
    private final CorporateActionRepository repository;

    public CorporateActionController(CorporateActionService service,
                                     CorporateActionRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @PostMapping("/corporate-actions")
    @ResponseStatus(HttpStatus.CREATED)
    public CorporateActionView register(@Valid @RequestBody CorporateActionRequest request) {
        CorporateAction action = service.register(request);
        return view(action, false);
    }

    @PostMapping("/accounts/{accountId}/corporate-actions/{actionId}/apply")
    public CorporateActionView apply(@PathVariable String accountId, @PathVariable String actionId) {
        service.apply(accountId, actionId);
        return view(repository.get(actionId), true);
    }

    @GetMapping("/corporate-actions/{actionId}")
    public CorporateActionView get(@PathVariable String actionId) {
        CorporateAction action = repository.get(actionId);
        if (action == null) {
            throw new com.github.lechandonga.portfolioriskenginejava.error.BusinessException(
                    com.github.lechandonga.portfolioriskenginejava.error.ErrorCode.VALIDATION_ERROR,
                    "corporate action not registered: " + actionId);
        }
        return view(action, false);
    }

    private CorporateActionView view(CorporateAction action, boolean applied) {
        return new CorporateActionView(action.getActionId(), action.getSymbol(), action.getType(),
                action.getRatio(), action.getCashPerShare(), action.getExDate(), applied);
    }
}
