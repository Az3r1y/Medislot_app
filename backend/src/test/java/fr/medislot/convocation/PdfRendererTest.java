package fr.medislot.convocation;

import static org.assertj.core.api.Assertions.*;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.file.*;
import java.util.Map;

class PdfRendererTest {
    static byte[] renderSample() throws Exception {
        var r = new ClassLoaderTemplateResolver();
        r.setPrefix("templates/");
        r.setSuffix(".html");
        r.setTemplateMode(TemplateMode.HTML);
        r.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(r);
        var view =
                new ConvocationView(
                        12L,
                        "jeudi 15 janvier 2026",
                        "9 h 30",
                        "Hélène Durand",
                        "Cardiologie",
                        "12 rue de la République",
                        "Lyon");
        byte[] pdf = new PdfRenderer(engine).render("convocation", Map.of("c", view));
        Files.write(Path.of("target/convocation-exemple.pdf"), pdf);
        return pdf;
    }

    @Test
    void printsAccentsTimeAndAddress() throws Exception {
        try (var doc = Loader.loadPDF(renderSample())) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text)
                    .contains(
                            "Hélène Durand",
                            "jeudi 15 janvier 2026 à 9 h 30",
                            "Cardiologie",
                            "12 rue de la République, Lyon",
                            "Rendez-vous n° 12");
        }
    }

    @Test
    void embedsFontsAndIsTaggedPdf() throws Exception {
        try (var doc = Loader.loadPDF(renderSample())) {
            var res = doc.getPage(0).getResources();
            for (var name : res.getFontNames()) {
                PDFont f = res.getFont(name);
                assertThat(f.isEmbedded()).as(f.getName()).isTrue();
            }
            assertThat(res.getFontNames()).isNotEmpty();
            assertThat(doc.getDocumentCatalog().getStructureTreeRoot())
                    .as("PDF balisé (PDF/UA)")
                    .isNotNull();
            assertThat(doc.getDocumentCatalog().getLanguage()).isEqualTo("fr");
            assertThat(doc.getDocumentInformation().getTitle())
                    .isEqualTo("Convocation MediSlot n° 12");
        }
    }
}
