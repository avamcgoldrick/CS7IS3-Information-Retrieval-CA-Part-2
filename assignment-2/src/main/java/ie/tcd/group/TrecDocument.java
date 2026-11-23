package ie.tcd.group;

/**
 * One document from the TREC collection after parsing and cleaning.
 *
 * @param docno  the DOCNO, matched against the QRels (e.g. "FT911-1")
 * @param source the folder it came from: ft, fbis, latimes or fr94
 * @param title  the headline or title, may be empty
 * @param body   the main text, may be empty
 */
public record TrecDocument(String docno, String source, String title, String body) {
}
