package org.hyperledger.fabric.samples.assettransfer; // Asegúrate que el package coincida

import java.util.List;
import com.owlike.genson.annotation.JsonProperty;

public class AggregatedModel {
    @JsonProperty("roundNumber")
    private String roundNumber;

    @JsonProperty("aggregatedWeights")
    private List<Double> aggregatedWeights;

    @JsonProperty("contributingNodeIds")
    private List<String> contributingNodeIds; // Para saber quién participó

    // Constructor vacío
    public AggregatedModel() {
    }

    public AggregatedModel(String roundNumber, List<Double> aggregatedWeights, List<String> contributingNodeIds) {
        this.roundNumber = roundNumber;
        this.aggregatedWeights = aggregatedWeights;
        this.contributingNodeIds = contributingNodeIds;
    }

    // Getters
    public String getRoundNumber() { return roundNumber; }
    public List<Double> getAggregatedWeights() { return aggregatedWeights; }
    public List<String> getContributingNodeIds() { return contributingNodeIds; }
}