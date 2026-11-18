package ie.tcd.group;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.util.CharsRef;

public class CustomEnglishAnalyzer extends Analyzer {

    private static final CharArraySet CUSTOM_STOP_WORDS =
            createCustomStopWords();

    private static final SynonymMap SYNONYMS =
            createSynonyms();

    // Create custom stop-word list
    private static CharArraySet createCustomStopWords() {

        CharArraySet words = new CharArraySet(
                EnglishAnalyzer.getDefaultStopSet(), true);

        // Cranfield formatting 
        words.addAll(Arrays.asList(
                "i", "b", "w", "j"
        ));

        words.addAll(Arrays.asList(
                "obtained",
                "presented",
                "shown",
                "given",
                "made",
                "found"
        ));

        return CharArraySet.unmodifiableSet(words);
    }

    // Create domain-specific synonym dictionary
    private static SynonymMap createSynonyms() {

        SynonymMap.Builder builder =
                new SynonymMap.Builder(true);

        try {

            builder.add(
                    new CharsRef("aerofoil"),
                    new CharsRef("airfoil"),
                    true
            );

            builder.add(
                    new CharsRef("airfoil"),
                    new CharsRef("aerofoil"),
                    true
            );

            builder.add(
                    new CharsRef("velocity"),
                    new CharsRef("speed"),
                    true
            );

            builder.add(
                    new CharsRef("speed"),
                    new CharsRef("velocity"),
                    true
            );

            builder.add(
                    new CharsRef("skin"),
                    new CharsRef("friction"),
                    true
            );

            builder.add(
                    new CharsRef("aircraft"),
                    new CharsRef("airplane"),
                    true
            );

            builder.add(
                    new CharsRef("airplane"),
                    new CharsRef("aircraft"),
                    true
            );

            return builder.build();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Error creating synonym map", e);
        }
    }

    @Override
    protected TokenStreamComponents createComponents(
            String fieldName) {

        Tokenizer source = new StandardTokenizer();

        TokenStream result = new LowerCaseFilter(source);

        result = new SynonymGraphFilter(
                result, SYNONYMS, true);

        result = new FlattenGraphFilter(result);

        result = new StopFilter(
                result, CUSTOM_STOP_WORDS);

        result = new PorterStemFilter(result);

        return new TokenStreamComponents(source, result);
    }
}
