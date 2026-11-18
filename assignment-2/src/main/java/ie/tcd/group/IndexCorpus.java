package ie.tcd.group;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;

public class IndexCorpus {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println(
                "Usage: IndexCorpus <corpus-path> [analyzer]");
            System.exit(1);
        }

        String analyzerName =
            args.length > 1 ? args[1] : "standard";

        ArrayList<Document> documents = new ArrayList<>();

        for (Path file : Common.collectFiles(new String[]{args[0]})) {
            System.out.printf("Indexing \"%s\"%n", file);

            Document doc = new Document();
            String content = Common.readFile(file);

            doc.add(new StringField(
                "filename", file.toString(), Field.Store.YES));
            doc.add(new TextField(
                "content", content, Field.Store.YES));

            documents.add(doc);
        }

        try (Analyzer analyzer =
                 ExperimentConfig.createAnalyzer(analyzerName);
             Directory directory = Common.openIndexDirectory()) {

            IndexWriter iwriter =
                Common.createIndexWriter(directory, analyzer);

            // Add all documents, then commit and close.
            iwriter.addDocuments(documents);
            iwriter.close();
        }

        System.out.println("Analyzer: " + analyzerName);
        System.out.println("Documents indexed: " + documents.size());
    }
}
