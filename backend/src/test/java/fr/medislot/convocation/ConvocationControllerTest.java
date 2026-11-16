package fr.medislot.convocation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

class ConvocationControllerTest {
    ConvocationService service = mock(ConvocationService.class);
    ConvocationController controller = new ConvocationController(service);

    {
        when(service.build("camille@test", 12L)).thenReturn("%PDF-".getBytes());
    }

    ResponseEntity<byte[]> get(boolean download) {
        return controller.convocation(() -> "camille@test", 12L, download);
    }

    @Test
    void returnsPdfContentType() {
        assertThat(get(false).getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    }

    @Test
    void inlineByDefaultWithServerFilename() {
        assertThat(get(false).getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("inline; filename=\"convocation-12.pdf\"");
    }

    @Test
    void attachmentWhenDownloadRequested() {
        assertThat(get(true).getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"convocation-12.pdf\"");
    }

    @Test
    void neverCachedNorSniffed() {
        var h = get(false).getHeaders();
        assertThat(h.getCacheControl()).isEqualTo("no-store");
        assertThat(h.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }
}
