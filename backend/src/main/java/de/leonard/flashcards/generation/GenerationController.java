package de.leonard.flashcards.generation;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.leonard.flashcards.generation.GenerationDtos.GenerationBudgetResponse;
import de.leonard.flashcards.generation.GenerationDtos.GenerationJobResponse;
import de.leonard.flashcards.generation.GenerationDtos.GenerationRequest;
import de.leonard.flashcards.llm.LlmClient;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/generation-jobs")
public class GenerationController {

    private final GenerationService service;
    private final GenerationRunner runner;
    private final LlmClient llmClient;

    public GenerationController(GenerationService service, GenerationRunner runner, LlmClient llmClient) {
        this.service = service;
        this.runner = runner;
        this.llmClient = llmClient;
    }

    /** Muss vor /{id} stehen, sonst versucht Spring "budget" als UUID zu lesen. */
    @GetMapping("/budget")
    public GenerationBudgetResponse budget() {
        return new GenerationBudgetResponse(
                llmClient.modelName(), llmClient.dailyRequestBudget(), llmClient.requestsPerMinute());
    }

    /**
     * Legt den Lauf an und gibt sofort 202 zurueck. Die Verarbeitung dauert je nach
     * Textmenge Minuten; das Frontend fragt den Fortschritt per Polling ab.
     */
    @PostMapping
    public ResponseEntity<GenerationJobResponse> create(@Valid @RequestBody GenerationRequest request) {
        GenerationJob job = service.createJob(request);
        runner.run(job.getId());

        return ResponseEntity
                .accepted()
                .location(URI.create("/api/generation-jobs/" + job.getId()))
                .body(GenerationJobResponse.from(job));
    }

    @GetMapping("/{id}")
    public GenerationJobResponse get(@PathVariable UUID id) {
        return GenerationJobResponse.from(service.findById(id));
    }

    @GetMapping
    public List<GenerationJobResponse> list() {
        return service.findAll().stream().map(GenerationJobResponse::from).toList();
    }
}
