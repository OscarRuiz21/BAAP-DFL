package org.hyperledger.fabric.samples.assettransfer; 

import java.util.List;
import com.owlike.genson.annotation.JsonProperty;

public class ModelUpdate {
    @JsonProperty("nodeId")
    private String nodeId;

    @JsonProperty("roundNumber")
    private String roundNumber;

    @JsonProperty("weights")
    private List<Double> weights;

    // Constructor vacío (necesario para Genson/Jackson)
    public ModelUpdate() {
    }

    public ModelUpdate(String nodeId, String roundNumber, List<Double> weights) {
        this.nodeId = nodeId;
        this.roundNumber = roundNumber;
        this.weights = weights;
    }

    // Getters (necesarios para Genson/Jackson y para tu lógica)
    public String getNodeId() { return nodeId; }
    public String getRoundNumber() { return roundNumber; }
    public List<Double> getWeights() { return weights; }

    // Setters (opcionales aquí si solo usas el constructor)
    // public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    // public void setRoundNumber(String roundNumber) { this.roundNumber = roundNumber; }
    // public void setWeights(List<Double> weights) { this.weights = weights; }
}