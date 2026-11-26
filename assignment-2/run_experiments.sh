#!/usr/bin/env bash
set -euo pipefail

JAR="target/ir-assignment-2.jar"
CORPUS="collection/Assignment Two"
QRELS="data/qrels.assignment2.part1"
RUN_DIR="runs"
RESULTS="results.tsv"

QUERY_TYPES=(title description narrative combined-title-description combined-title-narrative combined-description-narrative combined-all weighted-title)
ANALYZERS=(standard english whitespace custom)
MODELS=(bm25 bm25-low-k1 bm25-high-k1 bm25-low-b bm25-high-b bm25-ideal tfidf)

TREC_EVAL="./trec_eval/trec_eval"

for file in "$JAR" "$QRELS"; do
    if [[ ! -f "$file" ]]; then
        echo "ERROR: Missing $file"
        echo "Build the project with: mvn package"
        exit 1
    fi
done

if [[ ! -x "$TREC_EVAL" ]]; then
    echo "ERROR: $TREC_EVAL not found or not executable."
    exit 1
fi

for query_type in "${QUERY_TYPES[@]}"; do
    query_file="queries/${query_type}.qry"

    if [[ ! -f "$query_file" ]]; then
        echo "ERROR: Missing $query_file"
        exit 1
    fi
done

mkdir -p "$RUN_DIR"

printf "QueryType\tAnalyzer\tModel\tMAP\tRecall@5\tRecall@10\tRecall@20\tRecall@100\n" > "$RESULTS"

for analyzer in "${ANALYZERS[@]}"; do

    echo
    echo "======================================"
    echo "Building index: $analyzer"
    echo "======================================"

    java -jar "$JAR" index-trec "$CORPUS" "$analyzer" index

    for query_type in "${QUERY_TYPES[@]}"; do

        QUERIES="queries/${query_type}.qry"

        echo
        echo "--------------------------------------"
        echo "Query type: $query_type"
        echo "Analyzer: $analyzer"
        echo "--------------------------------------"

        for model in "${MODELS[@]}"; do

            run="$RUN_DIR/${query_type}-${model}-${analyzer}.run"
            tag="${query_type}_${model}_${analyzer}"

            echo
            echo "Running: query=$query_type analyzer=$analyzer model=$model"

            java -jar "$JAR" batch-query \
                "$QUERIES" "$run" "$analyzer" "$model" "$tag"

            echo "Evaluating: $run"

            metrics=$("$TREC_EVAL" \
                -m map \
                -m recall.5,10,20,100 \
                "$QRELS" "$run" | awk '
                    $1 == "map"        {map = $3}
                    $1 == "recall_5"   {r5 = $3}
                    $1 == "recall_10"  {r10 = $3}
                    $1 == "recall_20"  {r20 = $3}
                    $1 == "recall_100" {r100 = $3}
                    END {
                        if (map == "" || r5 == "" || r10 == "" || r20 == "" || r100 == "")
                            print "ERROR"
                        else
                            printf "%s\t%s\t%s\t%s\t%s", map, r5, r10, r20, r100
                    }')

            printf '%s\t%s\t%s\t%s\n' \
                "$query_type" "$analyzer" "$model" "$metrics" >> "$RESULTS"

            echo "Scores: $metrics"

        done
    done
done

echo
echo "======================================"
echo "ALL EXPERIMENTS COMPLETE"
echo "======================================"
echo "Results saved to: $RESULTS"
echo

column -t -s $'\t' "$RESULTS" 2>/dev/null || cat "$RESULTS"
