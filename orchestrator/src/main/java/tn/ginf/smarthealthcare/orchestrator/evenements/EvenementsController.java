package tn.ginf.smarthealthcare.orchestrator.evenements;

import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@code GET /events} : flux SSE des alertes en direct (P3.5), publié par la gateway avec le jeton JWT (D44).
 * Format : contracts/sse/README.md.
 */
@RestController
public class EvenementsController {

    /**
     * EventSource décode toujours en UTF-8, mais Spring écrit le texte des événements avec le charset de l'en-tête
     * Content-Type (ISO-8859-1 s'il est absent) : on le déclare explicitement pour les messages accentués.
     */
    static final MediaType TEXT_EVENT_STREAM_UTF8 = new MediaType("text", "event-stream", StandardCharsets.UTF_8);

    private final DiffusionSse diffusion;

    public EvenementsController(DiffusionSse diffusion) {
        this.diffusion = diffusion;
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> abonner() {
        return ResponseEntity.ok().contentType(TEXT_EVENT_STREAM_UTF8).body(diffusion.abonner());
    }
}
