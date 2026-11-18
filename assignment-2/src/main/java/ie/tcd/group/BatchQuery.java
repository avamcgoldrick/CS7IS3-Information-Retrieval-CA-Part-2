package ie.tcd.group;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.store.Directory;

public class BatchQuery {

    private static class CranfieldQuery {
        String id;
        String text;

        CranfieldQuery(String id, String text) {
            this.id = id;
            this.text = text;
        }
    }

    private static List<CranfieldQuery> readQueries(Path path)
            throws IOException {

        List<CranfieldQuery> queries = new ArrayList<>();
        String currentId = null;
        StringBuilder text = new StringBuilder();
        boolean readingText = false;
        int queryNumber = 0;

        for (String line : Files.readAllLines(
                path, StandardCharsets.ISO_8859_1)) {

            if (line.matches("^\\.I\\s+\\d+\\s*$")) {
                if (currentId != null) {
                    queries.add(new CranfieldQuery(
                        currentId, text.toString().trim()));
                }

                currentId = Integer.toString(++queryNumber);
                text.setLength(0);
                readingText = false;

            } else if (line.trim().equals(".W")) {
                readingText = true;

            } else if (readingText) {
                text.append(line).append(' ');
            }
        }

        if (currentId != null) {
            queries.add(new CranfieldQuery(
                currentId, text.toString().trim()));
        }

        return queries;
    }

    public static void main(String[] args) throws IOException {

        Path queryFile = Paths.get(
            args.length > 0 ? args[0] : "data/cran.qry");

        Path runFile = Paths.get(
            args.length > 1 ? args[1] : "runs/bm25-standard.run");

        String analyzerName =
            args.length > 2 ? args[2] : "standard";

        String similarityName =
            args.length > 3 ? args[3] : "bm25";

        String runTag =
            args.length > 4 ? args[4]
                : similarityName + "_" + analyzerName;

        Path parent = runFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<CranfieldQuery> queries = readQueries(queryFile);

        try (Analyzer analyzer =
                 ExperimentConfig.createAnalyzer(analyzerName);
             Directory directory = Common.openIndexDirectory();
             DirectoryReader reader = DirectoryReader.open(directory);
             BufferedWriter writer = Files.newBufferedWriter(
                 runFile, StandardCharsets.UTF_8)) {

            IndexSearcher searcher = new IndexSearcher(reader);

            Similarity similarity =
                ExperimentConfig.createSimilarity(similarityName);
            searcher.setSimilarity(similarity);

            QueryParser parser =
                new QueryParser("content", analyzer);

            int maxResults = 1400;

            for (CranfieldQuery cq : queries) {
                if (cq.text.isBlank()) {
                    System.err.println(
                        "Skipping empty query: " + cq.id);
                    continue;
                }

                try {
                    Query query = parser.parse(
                        QueryParser.escape(cq.text));

                    ScoreDoc[] hits =
                        searcher.search(query, maxResults).scoreDocs;

                    for (int rank = 0; rank < hits.length; rank++) {
                        Document doc = reader.storedFields()
                            .document(hits[rank].doc);

                        String filename = doc.get("filename");

                        if (filename == null) {
                            throw new IOException(
                                "Missing stored filename field.");
                        }

                        String docId = Paths.get(filename)
                            .getFileName().toString()
                            .replaceFirst("\\.txt$", "");

                        writer.write(String.format(
                            Locale.ROOT,
                            "%s Q0 %s %d %.8f %s%n",
                            cq.id,
                            docId,
                            rank + 1,
                            hits[rank].score,
                            runTag));
                    }

                    System.out.printf(
                        "Query %s: %d results%n",
                        cq.id, hits.length);

                } catch (ParseException e) {
                    System.err.println(
                        "Could not parse query " + cq.id + ": "
                            + e.getMessage());
                }
            }

            System.out.println("Queries processed: " + queries.size());
            System.out.println("Analyzer: " + analyzerName);
            System.out.println("Similarity: " + similarityName);
            System.out.println("Run file: " + runFile.toAbsolutePath());
        }
    }
}
