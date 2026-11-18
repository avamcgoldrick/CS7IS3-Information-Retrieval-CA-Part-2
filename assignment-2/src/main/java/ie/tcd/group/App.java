package ie.tcd.group;

import java.util.Arrays;

public class App {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String example = args[0];
        String[] exampleArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (example) {
            case "create-index" -> CreateIndex.main(exampleArgs);
            case "index-corpus" -> IndexCorpus.main(exampleArgs);
            case "boolean-query" -> BooleanQueries.main(exampleArgs);
            case "interactive-query" -> QueryIndex.main(exampleArgs);
            case "batch-query" -> BatchQuery.main(exampleArgs);
            case "stopword-candidates" -> StopWordCandidates.main(exampleArgs);
            case "synonym-candidates" -> SynonymCandidates.main(exampleArgs);
            case "postings-demo" -> PostingsDemo.main(exampleArgs);
            case "split-cranfield-data" -> SplitCranfieldData.main(exampleArgs);
            default -> {
                printUsage();
                System.exit(1);
            }
        }
    }    

    // Print how to run each example.
    private static void printUsage() {
        System.out.println("""
            Usage: java -jar target/ir-demo.jar <example> [args...]

            Examples:
              create-index                      Example 1: index a hard-coded document
              index-corpus <files|folders>      Example 2: index a corpus
              boolean-query                     Example 3: query the index with a hard-coded BooleanQuery
              interactive-query                 Example 4: interactive QueryParser
              postings-demo <files|folders>     Example 5: index the corpus with term vectors and print term stats
              split-cranfield-data              Example 6: split the cranfield dataset into individual files
              index-corpus <files|folders> [analyzer]
                    Index corpus using standard, english, whitespace, or custom

              batch-query [query-file] [run-file] [analyzer] [similarity] [run-tag]
                    Run batch queries using the selected analyzer and similarity
            
            Example command:
              java -jar target/ir-demo.jar index-corpus data/example/corpus
            """);
    }
}
