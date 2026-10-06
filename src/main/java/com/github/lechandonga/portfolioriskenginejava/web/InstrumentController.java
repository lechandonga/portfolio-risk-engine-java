package com.github.lechandonga.portfolioriskenginejava.web;

import com.github.lechandonga.portfolioriskenginejava.domain.Instrument;
import com.github.lechandonga.portfolioriskenginejava.dto.Requests.CreateInstrumentRequest;
import com.github.lechandonga.portfolioriskenginejava.service.InstrumentRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {
    private final InstrumentRepository instruments;

    public InstrumentController(InstrumentRepository instruments) {
        this.instruments = instruments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Instrument create(@Valid @RequestBody CreateInstrumentRequest request) {
        boolean active = request.active() == null || request.active();
        return instruments.save(new Instrument(request.symbol(),
                request.name() != null ? request.name() : request.symbol(), request.lotSize(),
                request.maxPriceDeviation(), active));
    }

    @GetMapping
    public List<Instrument> list() {
        return List.copyOf(instruments.findAll());
    }
}
