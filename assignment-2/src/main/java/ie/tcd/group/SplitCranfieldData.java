package ie.tcd.group;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SplitCranfieldData {

    private static final Charset INPUT_CHARSET =
            StandardCharsets.ISO_8859_1;

    private static final Pattern DOCUMENT_START =
            Pattern.compile("(?m)^\\s*\\.I\\s+(\\d+)\\s*$");

    public static void main(String[] args) throws IOException {
        Path inputFile = Paths.get("data", "cran.all.1400");
        Path outputDir = Paths.get("data", "corpus");

        Files.createDirectories(outputDir);

        String content = Files.readString(inputFile, INPUT_CHARSET);

        Matcher documentMatcher = DOCUMENT_START.matcher(content);
        List<Integer> startPositions = new ArrayList<>();
        List<String> documentIds = new ArrayList<>();

        while (documentMatcher.find()) {
            startPositions.add(documentMatcher.start());
            documentIds.add(documentMatcher.group(1));
        }

        if (startPositions.isEmpty()) {
            throw new IOException(
                    "No document records found. Check the cran.all.1400 format."
            );
        }

        int count = 0;

        for (int i = 0; i < startPositions.size(); i++) {
            int start = startPositions.get(i);
            int end = (i + 1 < startPositions.size())
                    ? startPositions.get(i + 1)
                    : content.length();

            String record = content.substring(start, end).strip();
            String docId = documentIds.get(i);

            Path outputFile = outputDir.resolve(docId + ".txt");

            Files.writeString(
                    outputFile,
                    record + System.lineSeparator(),
                    StandardCharsets.UTF_8
            );

            count++;
        }

        System.out.println("Documents processed: " + count);
        System.out.println(
                "Output directory: " + outputDir.toAbsolutePath()
        );
    }
}
