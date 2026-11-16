package fr.medislot.convocation;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;

// Le mode PDF/UA exige des polices embarquées.
@Component
public class PdfRenderer {
    static final String FAMILY = "Noto Sans";
    private final ITemplateEngine templates;

    public PdfRenderer(ITemplateEngine templates) {
        this.templates = templates;
    }

    public byte[] render(String template, Map<String, Object> variables) {
        String html = templates.process(template, new Context(Locale.FRANCE, variables));
        var out = new ByteArrayOutputStream();
        try {
            var b = new PdfRendererBuilder();
            b.useFastMode();
            b.usePdfUaAccessibility(true);
            font(b, "fonts/NotoSans-Regular.ttf", 400);
            font(b, "fonts/NotoSans-Bold.ttf", 700);
            b.withHtmlContent(html, null);
            b.toStream(out);
            b.run();
        } catch (IOException e) {
            throw new UncheckedIOException("RenderingFailed", e);
        }
        return out.toByteArray();
    }

    private static void font(PdfRendererBuilder b, String path, int weight) {
        b.useFont(
                () -> PdfRenderer.class.getClassLoader().getResourceAsStream(path),
                FAMILY,
                weight,
                PdfRendererBuilder.FontStyle.NORMAL,
                true);
    }
}
