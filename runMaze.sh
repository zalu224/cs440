#!/bin/bash

# Set the number of runs
NUM_RUNS=50
WIN_COUNT=0

# Run the Java command and count wins
for ((i=1; i<=$NUM_RUNS; i++)); do
    OUTPUT=$(javac -cp "./lib/*:." @infexf.srcs)
    OUTPUT=$(java -cp "./lib/*:." edu.cwru.sepia.Main2 data/labs/infexf/TwoUnitSmallMaze.xml)
    if [[ $OUTPUT == *"The enemy was destroyed, you win!"* ]]; then
        ((WIN_COUNT++))
    fi
done

# Display results
echo "Total runs: $NUM_RUNS"
echo "Wins: $WIN_COUNT"