package fr.medislot.convocation;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api")
public class ConvocationController {
    private final ConvocationService service;

    public ConvocationController(ConvocationService service) {
        this.service = service;
    }

    @GetMapping("/appointments/{id}/convocation.pdf")
    public ResponseEntity<byte[]> convocation(
            Principal p,
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean download) {
        byte[] pdf = service.build(p.getName(), id);
        var disposition =
                (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                        .filename(ConvocationService.filename(id))
                        .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }
}
