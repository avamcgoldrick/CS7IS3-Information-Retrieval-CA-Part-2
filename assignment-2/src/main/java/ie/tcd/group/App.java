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
            case "interactive-query" -> QueryIndex.main(exampleArgs);
            case "batch-query" -> BatchQuery.main(exampleArgs);
            case "parse-trec" -> TrecParser.main(exampleArgs);
            case "index-trec" -> IndexTrec.main(exampleArgs);
            default -> {
                printUsage();
                System.exit(1);
            }
        }
    }    

    // Print how to run each command.
    private static void printUsage() {
        System.out.println("""
            Usage: java -jar target/ir-assignment-2.jar <command> [args...]

            Commands:
              parse-trec [collection-root]
                    Parse the TREC collection without indexing and print counts per source
              index-trec [collection-root] [analyzer] [index-dir]
                    Index the TREC collection (defaults: "collection/Assignment Two", english, index)
              interactive-query
                    Search the index interactively
              batch-query [query-file] [run-file] [analyzer] [similarity] [run-tag]
                    Run batch queries using the selected analyzer and similarity

            Example command:
              java -jar target/ir-assignment-2.jar index-trec
            """);
    }
}
