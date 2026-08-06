package de.leonard.flashcards.deck;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import de.leonard.flashcards.config.ApiExceptionHandler;

@WebMvcTest(FolderController.class)
@Import(ApiExceptionHandler.class)
class FolderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FolderRepository repository;

    @Test
    void listReturnsFoldersByName() throws Exception {
        given(repository.findAllByOrderByNameAsc())
                .willReturn(List.of(new Folder("Anatomie"), new Folder("ITIL")));

        mockMvc.perform(get("/api/folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Anatomie"))
                .andExpect(jsonPath("$[1].name").value("ITIL"));
    }

    @Test
    void createTrimsTheName() throws Exception {
        given(repository.save(any(Folder.class))).willAnswer(call -> call.getArgument(0));

        mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  Semester 3  "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Semester 3"));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Der Name darf nicht leer sein."));
    }

    @Test
    void patchRenamesTheFolder() throws Exception {
        Folder folder = new Folder("Alt");
        given(repository.findById(folder.getId())).willReturn(Optional.of(folder));

        mockMvc.perform(patch("/api/folders/{id}", folder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Neu"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Neu"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        given(repository.existsById(id)).willReturn(true);

        mockMvc.perform(delete("/api/folders/{id}", id)).andExpect(status().isNoContent());

        verify(repository).deleteById(id);
    }

    @Test
    void deleteOfUnknownFolderReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        given(repository.existsById(id)).willReturn(false);

        mockMvc.perform(delete("/api/folders/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ordner nicht gefunden: " + id));
    }
}
