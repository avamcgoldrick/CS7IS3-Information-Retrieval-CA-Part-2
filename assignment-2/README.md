# CS7IS3 CA Part 2 — Group search engine

Lucene 10.5 search engine over the TREC Disks 4 & 5 collection (FT, FBIS, LA Times, Federal Register; 528,155 documents).

## Requirements

- Java 21+
- Maven 3.9+
- gcc and make (to build trec_eval)

On an Ubuntu VM:

```
sudo apt install openjdk-21-jdk-headless maven build-essential
```

## Setup

```
# 1. Build trec_eval (once per machine; the binary is not committed)
cd trec_eval && make && cd ..
./trec_eval/trec_eval -v        # should print trec_eval version 10.0-rc3

# 2. Build the search engine
mvn package                     # produces target/ir-assignment-2.jar
```

## Layout

| Path | Contents |
|---|---|
| `collection/Assignment Two/` | The document collection (ft, fbis, latimes, fr94) |
| `data/qrels.assignment2.part1` | Phase 1 relevance judgements, topics 401–425 |
| `data/topics` | Topics file from Blackboard (to add) |
| `src/main/java/ie/tcd/group/` | Search engine source |
| `trec_eval/` | trec_eval source |
| `index/`, `runs/` | Generated locally, git-ignored |

## Evaluating a run

```
./trec_eval/trec_eval -m map -m P.10 -m ndcg_cut.10 -m recall.1000 \
    data/qrels.assignment2.part1 runs/<run-file>
```

Run files use the TREC format `topic Q0 DOCNO rank score tag`, with 1,000 results per topic. Results are matched on DOCNO.

## Status

The code in `src/` is still the CA Part 1 (Cranfield) skeleton. The TREC parser, topic parser and batch search are next.
