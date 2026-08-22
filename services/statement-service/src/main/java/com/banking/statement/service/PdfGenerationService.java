package com.banking.statement.service;

import com.banking.statement.entity.StatementTransaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class PdfGenerationService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("UTC"));

    public byte[] generate(UUID accountId, int month, int year,
                           BigDecimal openingBalance, BigDecimal closingBalance,
                           BigDecimal totalCredits, BigDecimal totalDebits,
                           List<StatementTransaction> transactions) throws IOException {

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float margin = 50;
            float yStart = page.getMediaBox().getHeight() - margin;
            float lineHeight = 18;
            float tableTop;

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {

                // ── Header ──────────────────────────────────────────────────
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                cs.newLineAtOffset(margin, yStart);
                cs.showText("BANKING PLATFORM - Account Statement");
                cs.endText();

                float y = yStart - lineHeight * 2;

                // ── Metadata ─────────────────────────────────────────────────
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);

                y = drawLabelValue(cs, margin, y, lineHeight, "Account ID:", accountId.toString());
                y = drawLabelValue(cs, margin, y, lineHeight, "Period:", month + " / " + year);
                y = drawLabelValue(cs, margin, y, lineHeight, "Opening Balance:", "INR " + openingBalance);
                y = drawLabelValue(cs, margin, y, lineHeight, "Closing Balance:", "INR " + closingBalance);
                y = drawLabelValue(cs, margin, y, lineHeight, "Total Credits:", "INR " + totalCredits);
                y = drawLabelValue(cs, margin, y, lineHeight, "Total Debits:", "INR " + totalDebits);
                y = drawLabelValue(cs, margin, y, lineHeight, "Transaction Count:", String.valueOf(transactions.size()));

                y -= lineHeight;

                // ── Table header ──────────────────────────────────────────────
                tableTop = y;
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
                cs.beginText();
                cs.newLineAtOffset(margin, tableTop);
                cs.showText(String.format("%-22s %-12s %-30s %10s %8s",
                        "Date", "Type", "Description", "Amount", "Dir"));
                cs.endText();

                y -= 4;
                drawHLine(cs, margin, y, page.getMediaBox().getWidth() - margin);
                y -= lineHeight;

                // ── Transactions ──────────────────────────────────────────────
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                for (StatementTransaction txn : transactions) {
                    if (y < margin + lineHeight) {
                        // Simple overflow protection: stop rendering
                        break;
                    }
                    String date = DATE_FMT.format(txn.getTransactedAt());
                    String type = nvl(txn.getTransactionType(), "-");
                    String desc = truncate(nvl(txn.getDescription(), "-"), 28);
                    String amount = txn.getAmount().toPlainString();
                    String dir = txn.getDirection();

                    cs.beginText();
                    cs.newLineAtOffset(margin, y);
                    cs.showText(String.format("%-22s %-12s %-30s %10s %8s",
                            date, type, desc, amount, dir));
                    cs.endText();
                    y -= lineHeight;
                }

                // ── Footer ────────────────────────────────────────────────────
                y -= lineHeight;
                drawHLine(cs, margin, y, page.getMediaBox().getWidth() - margin);
                y -= lineHeight;
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 8);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Generated at: " + DATE_FMT.format(Instant.now()) + " UTC | This is a system-generated statement.");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private float drawLabelValue(PDPageContentStream cs, float x, float y,
                                  float lineHeight, String label, String value) throws IOException {
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
        cs.showText(label + " ");
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
        cs.showText(value);
        cs.endText();
        return y - lineHeight;
    }

    private void drawHLine(PDPageContentStream cs, float x1, float y, float x2) throws IOException {
        cs.moveTo(x1, y);
        cs.lineTo(x2, y);
        cs.stroke();
    }

    private String nvl(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
