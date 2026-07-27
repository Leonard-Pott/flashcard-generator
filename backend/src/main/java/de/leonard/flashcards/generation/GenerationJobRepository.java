package de.leonard.flashcards.generation;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {

    List<GenerationJob> findAllByOrderByCreatedAtDesc();
}
