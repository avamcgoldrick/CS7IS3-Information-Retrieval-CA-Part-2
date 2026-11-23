package ie.tcd.group;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Parses the four SGML sources of the TREC collection (FT, FBIS, LA Times,
 * Federal Register) into {@link TrecDocument}s.
 *
 * The files are SGML, not XML: there is no root element, each file holds many
 * documents, and they use undeclared entities such as &amp;hyph;. So instead of
 * an XML parser, each file is split on DOC tags and fields are pulled out by
 * looking for their opening and closing tags.
 *
 * Run on its own (parse-trec) it parses the whole collection without building
 * an index and prints per-source counts and a sample document from each source.
 */
public final class TrecParser {

    /** Source folders under the collection root. */
    public static final String[] SOURCES = {"ft", "fbis", "latimes", "fr94"};

    /** Number of documents in TREC Disks 4 & 5 minus the Congressional Record. */
    public static final int EXPECTED_DOCS = 528_155;

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&[a-zA-Z]+;");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Every FT headline starts with the paper and date, e.g. "FT  14 MAY 91 / ". */
    private static final Pattern FT_HEADLINE_PREFIX =
            Pattern.compile("^FT\\s+\\d{1,2}\\s+[A-Z]{3}\\s+\\d{2}\\s*/\\s*");

    /** Federal Register sections that hold contact details and filing codes, not content. */
    private static final String[] FR94_NOISE_TAGS = {
        "ADDRESS", "FURTHER", "BILLING", "FRFILING", "SIGNER", "SIGNJOB",
        "CFRNO", "RINDOCK", "FOOTCITE", "FOOTNAME"
    };

    private TrecParser() {
    }

    /**
     * Lists every data file under the collection root, skipping the readme
     * files that sit inside each source folder and the dtds folder.
     */
    public static List<Path> collectFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String source : SOURCES) {
            Path dir = root.resolve(source);
            if (!Files.isDirectory(dir)) {
                throw new IOException("Missing source folder: " + dir.toAbsolutePath());
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> isDataFile(p.getFileName().toString()))
                    .sorted()
                    .forEach(files::add);
            }
        }
        return files;
    }

    private static boolean isDataFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return !lower.startsWith("read") && !lower.startsWith(".");
    }

    /** The source a file belongs to, i.e. its top-level folder under the root. */
    public static String sourceOf(Path root, Path file) {
        return root.relativize(file).getName(0).toString();
    }

    /**
     * Parses every document in one file and hands each to the sink.
     *
     * @return the number of documents found in the file
     */
    public static int parseFile(Path file, String source, Consumer<TrecDocument> sink)
            throws IOException {
        String content = Files.readString(file, StandardCharsets.ISO_8859_1);
        int count = 0;
        int pos = 0;
        while (true) {
            int start = content.indexOf("<DOC>", pos);
            if (start < 0) {
                break;
            }
            int end = content.indexOf("</DOC>", start);
            if (end < 0) {
                throw new IOException("Unclosed <DOC> in " + file);
            }
            TrecDocument doc = parseDocument(content.substring(start + "<DOC>".length(), end), source);
            if (doc.docno().isEmpty()) {
                throw new IOException("Document without a DOCNO in " + file);
            }
            sink.accept(doc);
            count++;
            pos = end + "</DOC>".length();
        }
        return count;
    }

    /** Extracts the DOCNO, title and body from the text between DOC tags. */
    static TrecDocument parseDocument(String raw, String source) {
        // The DOCNO is padded with spaces in FBIS, LA Times and FR94; the QRels are not.
        String docno = firstElement(raw, "DOCNO").trim();
        String title;
        String body;

        switch (source) {
            case "ft" -> {
                title = FT_HEADLINE_PREFIX.matcher(clean(firstElement(raw, "HEADLINE"))).replaceFirst("");
                body = clean(allElements(raw, "TEXT"));
            }
            case "latimes" -> {
                // About 4,000 LA Times documents have no TEXT, only a headline or a
                // graphic caption, so the caption is kept to keep them findable.
                title = clean(firstElement(raw, "HEADLINE"));
                body = clean(allElements(raw, "TEXT") + " " + allElements(raw, "GRAPHIC"));
            }
            case "fbis" -> {
                title = clean(firstElement(raw, "TI"));
                body = clean(allElements(raw, "TEXT"));
            }
            case "fr94" -> {
                // In FR94 the title and summary are nested inside TEXT, along with
                // contact details and filing codes that are removed here.
                String text = COMMENT.matcher(allElements(raw, "TEXT")).replaceAll(" ");
                for (String tag : FR94_NOISE_TAGS) {
                    text = removeElements(text, tag);
                }
                title = clean(firstElement(text, "DOCTITLE"));
                body = clean(removeElements(text, "DOCTITLE"));
            }
            default -> throw new IllegalArgumentException("Unknown source: " + source);
        }
        return new TrecDocument(docno, source, title, body);
    }

    /** The text inside the first TAG element, or "" if there is none. */
    static String firstElement(String text, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int start = text.indexOf(open);
        if (start < 0) {
            return "";
        }
        int end = text.indexOf(close, start);
        if (end < 0) {
            return "";
        }
        return text.substring(start + open.length(), end);
    }

    /** The text inside every TAG element, joined with spaces. */
    static String allElements(String text, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        StringBuilder out = new StringBuilder();
        int pos = 0;
        while (true) {
            int start = text.indexOf(open, pos);
            if (start < 0) {
                break;
            }
            int end = text.indexOf(close, start);
            if (end < 0) {
                break;
            }
            out.append(text, start + open.length(), end).append(' ');
            pos = end + close.length();
        }
        return out.toString();
    }

    /** The text with every TAG element (tags and contents) removed. */
    static String removeElements(String text, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        StringBuilder out = new StringBuilder();
        int pos = 0;
        while (true) {
            int start = text.indexOf(open, pos);
            if (start < 0) {
                break;
            }
            int end = text.indexOf(close, start);
            if (end < 0) {
                break;
            }
            out.append(text, pos, start).append(' ');
            pos = end + close.length();
        }
        out.append(text, pos, text.length());
        return out.toString();
    }

    /**
     * Turns SGML into plain text: drops comments and tags (keeping the text
     * between them), decodes entities, and collapses whitespace. Tags are
     * removed before entities are decoded so that &amp;lt; cannot create a tag.
     */
    static String clean(String text) {
        String s = COMMENT.matcher(text).replaceAll(" ");
        s = TAG.matcher(s).replaceAll(" ");
        s = decodeEntities(s);
        return WHITESPACE.matcher(s).replaceAll(" ").trim();
    }

    private static String decodeEntities(String text) {
        if (text.indexOf('&') < 0) {
            return text;
        }
        Matcher m = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (m.find()) {
            String replacement = switch (m.group()) {
                case "&hyph;" -> "-";
                case "&amp;" -> "&";
                case "&lt;" -> "<";
                case "&gt;" -> ">";
                default -> " "; // &blank;, &sect;, &bull; and other symbols
            };
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Parses the whole collection without indexing and prints per-source counts
     * plus one sample document per source, as a quick check of the parser.
     *
     * Usage: parse-trec [collection-root]
     */
    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args.length > 0 ? args[0] : "collection/Assignment Two");
        List<Path> files = collectFiles(root);
        System.out.printf("Parsing %d files under %s%n", files.size(), root);

        Map<String, Integer> counts = new TreeMap<>();
        Map<String, Integer> noTitle = new TreeMap<>();
        Map<String, Integer> noBody = new TreeMap<>();
        Map<String, TrecDocument> samples = new TreeMap<>();
        long start = System.nanoTime();

        for (Path file : files) {
            String source = sourceOf(root, file);
            parseFile(file, source, doc -> {
                counts.merge(source, 1, Integer::sum);
                if (doc.title().isEmpty()) {
                    noTitle.merge(source, 1, Integer::sum);
                }
                if (doc.body().isEmpty()) {
                    noBody.merge(source, 1, Integer::sum);
                }
                samples.putIfAbsent(source, doc);
            });
        }

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        System.out.printf("Parsed in %.1f s%n%n", (System.nanoTime() - start) / 1e9);
        System.out.printf("%-8s %9s %9s %9s%n", "source", "docs", "no title", "no body");
        for (String source : counts.keySet()) {
            System.out.printf("%-8s %,9d %,9d %,9d%n", source, counts.get(source),
                    noTitle.getOrDefault(source, 0), noBody.getOrDefault(source, 0));
        }
        System.out.printf("%-8s %,9d (expected %,d)%n%n", "total", total, EXPECTED_DOCS);

        for (TrecDocument doc : samples.values()) {
            System.out.printf("[%s] %s%n  title: %s%n  body:  %s%n%n", doc.source(), doc.docno(),
                    doc.title(), abbreviate(doc.body(), 200));
        }

        if (total != EXPECTED_DOCS) {
            System.err.printf("ERROR: parsed %,d documents, expected %,d%n", total, EXPECTED_DOCS);
            System.exit(1);
        }
    }

    private static String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
