package ie.tcd.group;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;

public final class ExperimentConfig {

    private ExperimentConfig() {}

    public static Analyzer createAnalyzer(String name) {
        switch (name.toLowerCase()) {
            case "standard":
                return new StandardAnalyzer();

            case "english":
                return new EnglishAnalyzer();

            case "whitespace":
                return new WhitespaceAnalyzer();

            case "custom":
                return new CustomEnglishAnalyzer();
            
            case "unstemmed":
                return new UnstemmedEnglishAnalyzer();

            default:
                throw new IllegalArgumentException(
                    "Unknown analyzer: " + name);
        }
    }

    public static Similarity createSimilarity(String name) {
        switch (name.toLowerCase()) {
            case "bm25":
                return new BM25Similarity(1.2f, 0.75f);

            case "bm25-low-k1":
                return new BM25Similarity(0.5f, 0.75f);

            case "bm25-high-k1":
                return new BM25Similarity(2.0f, 0.75f);

            case "bm25-low-b":
                return new BM25Similarity(1.2f, 0.25f);

            case "bm25-high-b":
                return new BM25Similarity(1.2f, 1.0f);

            case "bm25-ideal":
                return new BM25Similarity(2.0f, 1.0f);

            case "tfidf":
                return new ClassicSimilarity();

            default:
                throw new IllegalArgumentException(
                    "Unknown similarity: " + name);
        }
    }
}
