package de.leonard.flashcards.deck;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    List<Folder> findAllByOrderByNameAsc();
}
