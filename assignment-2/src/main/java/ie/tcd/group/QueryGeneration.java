package ie.tcd.group;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryGeneration {

    private static class Topic {
        String id;
        String title;
        String description;
        String narrative;

        Topic(String id, String title, String description, String narrative) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.narrative = narrative;
        }
    }

    private static final Pattern TOPIC_PATTERN = 
        Pattern.compile("(?is)<top>(.*?)</top>");

    private static final Pattern NUMBER_PATTERN = 
        Pattern.compile("(?is)<num>\\s*Number:\\s*(\\d+)");

    private static final Pattern TITLE_PATTERN =
        Pattern.compile("(?is)<title>\\s*(.*?)\\s*(?=<desc>)");
    
    private static final Pattern DESCRIPTION_PATTERN =
        Pattern.compile("(?is)<desc>\\s*(?:Description:\\s*)?(.*?)\\s*(?=<narr>)");

    private static final Pattern NARRATIVE_PATTERN =
        Pattern.compile("(?is)<narr>\\s*(?:Narrative:\\s*)?(.*)");
    
    private static String extract(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return cleanText(matcher.group(1));
        }

        return "";
    }

    private static String cleanText(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static List<Topic> readTopics(Path path) throws IOException {
        List<Topic> topics = new ArrayList<>();
        String content = Files.readString(path, StandardCharsets.ISO_8859_1);
        Matcher matcher = TOPIC_PATTERN.matcher(content);

        while (matcher.find()) {
            String topicBlock = matcher.group(1);
            Matcher numberMatcher = NUMBER_PATTERN.matcher(topicBlock);

            if (!numberMatcher.find()) {
                System.err.println("Warning: No number found for topic:\n" + topicBlock);
                continue; // Skip if no number found
            }

            String id = numberMatcher.group(1);
            String title = extract(TITLE_PATTERN, topicBlock);
            String description = extract(DESCRIPTION_PATTERN, topicBlock);
            String narrative = extract(NARRATIVE_PATTERN, topicBlock);

            topics.add(new Topic(id, title, description, narrative));
        }
        return topics;
    }
    
    private static void writeQueries(List<Topic> topics, Path outputPath, String queryType) throws IOException {
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                for (Topic topic : topics) {
                    String queryText;

                    switch (queryType) {
                        case "title":
                            queryText = topic.title;
                            break;
                        case "description":
                            queryText = topic.description;
                            break;
                        case "narrative":
                            queryText = topic.narrative;
                            break;
                        case "combined":
                            queryText = String.join(" ", topic.title, topic.description, topic.narrative);
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown query type: " + queryType);
                    }

                    queryText = cleanText(queryText);

                    if(queryText.isBlank()) {
                        System.err.println("Warning: Empty query for topic ID " + topic.id);
                        continue; // Skip empty queries
                    }

                    writer.write(".I " + topic.id + "\n");
                    writer.write(".W\n");
                    writer.write(queryText + "\n");
                }
            }
        }

public static void main(String[] args) throws IOException {

        Path topicFile = Paths.get(
            args.length > 0 ? args[0] : "data/topics"
        );

        Path outputDirectory = Paths.get(
            args.length > 1 ? args[1] : "queries"
        );

        if (!Files.exists(topicFile)) {
            throw new IOException(
                "Topic file not found: " + topicFile.toAbsolutePath()
            );
        }

        Files.createDirectories(outputDirectory);

        List<Topic> topics = readTopics(topicFile);

        if (topics.isEmpty()) {
            throw new IOException(
                "No topics were found in: " + topicFile.toAbsolutePath()
            );
        }

        writeQueries(
            topics,
            outputDirectory.resolve("title.qry"),
            "title"
        );

        writeQueries(
            topics,
            outputDirectory.resolve("description.qry"),
            "description"
        );

        writeQueries(
            topics,
            outputDirectory.resolve("narrative.qry"),
            "narrative"
        );

        writeQueries(
            topics,
            outputDirectory.resolve("combined.qry"),
            "combined"
        );

        System.out.println("Topics read: " + topics.size());

        System.out.println(
            "Generated query files in: "
                + outputDirectory.toAbsolutePath()
        );

        System.out.println(" - title.qry");
        System.out.println(" - description.qry");
        System.out.println(" - narrative.qry");
        System.out.println(" - combined.qry");
    }
}