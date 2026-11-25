#!/usr/bin/env bash
set -euo pipefail


JAR="target/ir-assignment-2.jar"
CORPUS="collection/Assignment Two"
QUERIES="data/collection.qry"
QRELS="data/qrels.assignment2.part1"
RUN_DIR="runs"
RESULTS="results.tsv"

ANALYZERS=(standard english whitespace custom)
MODELS=(bm25 bm25-low-k1 bm25-high-k1 bm25-low-b bm25-high-b bm25-ideal tfidf)

for file in "$JAR" "$QUERIES" "$QRELS"; do
    if [[ ! -f "$file" ]]; then
        echo "ERROR: Missing $file"
        echo "Build the project with: mvn package"
        exit 1
    fi
done

TREC_EVAL="./trec_eval/trec_eval"

if [[ ! -x "$TREC_EVAL" ]]; then
    echo "ERROR: $TREC_EVAL not found or not executable."
    exit 1
fi

mkdir -p "$RUN_DIR"
printf "Analyzer\tModel\tMAP\tRecall@5\tRecall@10\tRecall@20\tRecall@100\n" > "$RESULTS"

for analyzer in "${ANALYZERS[@]}"; do
    echo
    echo "======================================"
    echo "Building index: $analyzer"
    echo "======================================"

    java -jar "$JAR" index-trec "$CORPUS" "$analyzer" index

    for model in "${MODELS[@]}"; do
        run="$RUN_DIR/${model}-${analyzer}.run"
        tag="${model}_${analyzer}"

        echo
        echo "Running: analyzer=$analyzer model=$model"

        java -jar "$JAR" batch-query \
            "$QUERIES" "$run" "$analyzer" "$model" "$tag"

        echo "Evaluating: $run"

        metrics=$("$TREC_EVAL" \
            -m map \
            -m recall.5,10,20,100 \
            "$QRELS" "$run" | awk '
                $1 == "map"       {map = $3}
                $1 == "recall_5"  {r5 = $3}
                $1 == "recall_10" {r10 = $3}
                $1 == "recall_20" {r20 = $3}
                $1 == "recall_100" {r100 = $3}
                END {
                if (map == "" || r5 == "" || r10 == "" || r20 == "" || r100 == "")
                    print "ERROR"
                else
                    printf "%s\t%s\t%s\t%s\t%s", map, r5, r10, r20, r100
                }')

        printf '%s\t%s\t%s\n' \
            "$analyzer" "$model" "$metrics" >> "$RESULTS"

        echo "Scores: $metrics"
    done
done

echo
echo "======================================"
echo "ALL EXPERIMENTS COMPLETE"
echo "======================================"
echo "Results saved to: $RESULTS"
echo
column -t -s $'\t' "$RESULTS" 2>/dev/null || cat "$RESULTS"
