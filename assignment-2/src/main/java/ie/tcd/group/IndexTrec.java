package ie.tcd.group;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Builds a Lucene index over the whole TREC collection.
 *
 * Fields:
 *   docno   - exact DOCNO, stored; written to the run file
 *   source  - ft, fbis, latimes or fr94, stored; for per-source analysis
 *   title   - headline/title, stored so results can be inspected
 *   body    - main text, not stored
 *   content - title + body, with term vectors for query expansion (RM3)
 *
 * Usage: index-trec [collection-root] [analyzer] [index-dir]
 *   defaults: "collection/Assignment Two", english, index
 */
public class IndexTrec {

    public static final String FIELD_DOCNO = "docno";
    public static final String FIELD_SOURCE = "source";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_BODY = "body";
    public static final String FIELD_CONTENT = "content";

    /**
     * Same as a normal text field, plus term vectors: pseudo-relevance feedback
     * reads the term frequencies of the top-ranked documents from these.
     */
    private static final FieldType CONTENT_TYPE = new FieldType(TextField.TYPE_NOT_STORED);

    static {
        CONTENT_TYPE.setStoreTermVectors(true);
        CONTENT_TYPE.freeze();
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : "collection/Assignment Two");
        String analyzerName = args.length > 1 ? args[1] : "english";
        Path indexDir = Paths.get(args.length > 2 ? args[2] : Common.INDEX_DIR);

        List<Path> files = TrecParser.collectFiles(root);
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        System.out.printf("Indexing %d files from %s with the '%s' analyzer into %s (%d threads)%n",
                files.size(), root, analyzerName, indexDir, threads);

        Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        AtomicInteger filesDone = new AtomicInteger();
        long start = System.nanoTime();

        try (Analyzer analyzer = ExperimentConfig.createAnalyzer(analyzerName);
             Directory directory = FSDirectory.open(indexDir)) {

            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            config.setRAMBufferSizeMB(256);

            try (IndexWriter writer = new IndexWriter(directory, config)) {
                // IndexWriter is thread-safe, so files are parsed and added in parallel.
                // Each document is written as soon as it is parsed; nothing is held in a list.
                ExecutorService pool = Executors.newFixedThreadPool(threads);
                List<Future<Integer>> results = new ArrayList<>();
                for (Path file : files) {
                    String source = TrecParser.sourceOf(root, file);
                    results.add(pool.submit(() -> {
                        int n = TrecParser.parseFile(file, source, doc -> addDocument(writer, doc));
                        counts.computeIfAbsent(source, k -> new AtomicInteger()).addAndGet(n);
                        int done = filesDone.incrementAndGet();
                        if (done % 250 == 0 || done == files.size()) {
                            System.out.printf("  %d/%d files (%.0f s)%n", done, files.size(),
                                    (System.nanoTime() - start) / 1e9);
                        }
                        return n;
                    }));
                }
                pool.shutdown();
                for (Future<Integer> result : results) {
                    result.get(); // rethrows any parsing or indexing error
                }
                writer.commit();
            }
        }

        Map<String, Integer> sorted = new TreeMap<>();
        counts.forEach((source, n) -> sorted.put(source, n.get()));
        int total = sorted.values().stream().mapToInt(Integer::intValue).sum();

        System.out.printf("%nIndexed in %.0f s%n", (System.nanoTime() - start) / 1e9);
        sorted.forEach((source, n) -> System.out.printf("  %-8s %,9d%n", source, n));
        System.out.printf("  %-8s %,9d (expected %,d)%n", "total", total, TrecParser.EXPECTED_DOCS);

        if (total != TrecParser.EXPECTED_DOCS) {
            System.err.printf("ERROR: indexed %,d documents, expected %,d%n", total, TrecParser.EXPECTED_DOCS);
            System.exit(1);
        }
    }

    private static void addDocument(IndexWriter writer, TrecDocument doc) {
        Document d = new Document();
        d.add(new StringField(FIELD_DOCNO, doc.docno(), Field.Store.YES));
        d.add(new StringField(FIELD_SOURCE, doc.source(), Field.Store.YES));
        d.add(new TextField(FIELD_TITLE, doc.title(), Field.Store.YES));
        d.add(new TextField(FIELD_BODY, doc.body(), Field.Store.NO));
        d.add(new Field(FIELD_CONTENT, doc.title() + " " + doc.body(), CONTENT_TYPE));
        try {
            writer.addDocument(d);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
