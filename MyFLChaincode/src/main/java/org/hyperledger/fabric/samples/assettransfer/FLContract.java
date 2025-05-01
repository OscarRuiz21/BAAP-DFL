/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.assettransfer;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;


import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import com.owlike.genson.Genson;

@Contract(
        name = "FLContract", // Nombre del contrato
        info = @Info(
                title = "Federated Learning Contract",
                description = "Basic chaincode for Federated Learning prototype",
                version = "0.0.1",
                license = @License(name = "Apache-2.0"),
                contact = @Contact(email = "fl@example.com")))           
@Default
public final class FLContract implements ContractInterface { /

    private final Genson genson = new Genson(); // Para manejar JSON

    /**
     * Guarda la actualización de un modelo enviada por un nodo.
     * ctx: Contexto de la transacción (da acceso al ledger).
     * nodeId: Identificador del nodo (ej. "Node1").
     * roundNumber: Número de la ronda de entrenamiento (ej. "1").
     * modelUpdateJson: Los datos de la actualización (pesos) en formato JSON.
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT) // SUBMIT porque modifica el ledger
    public void submitModelUpdate(final Context ctx, final String nodeId, final String roundNumber, final String modelUpdateJson) {
        ChaincodeStub stub = ctx.getStub();

        // Validar entradas básicas (podrías añadir más validaciones)
        if (nodeId == null || nodeId.isEmpty() || roundNumber == null || roundNumber.isEmpty() || modelUpdateJson == null || modelUpdateJson.isEmpty()) {
            throw new ChaincodeException("Invalid input: nodeId, roundNumber, and modelUpdateJson cannot be empty");
        }
        // Podrías deserializar aquí para validar el JSON, pero por simplicidad lo guardamos directo.
        // ModelUpdate update = genson.deserialize(modelUpdateJson, ModelUpdate.class);

        // Creamos una clave única para guardar esta actualización
        String key = String.format("UPDATE_%s_%s", roundNumber, nodeId);
        stub.putStringState(key, modelUpdateJson); // Guarda el JSON en el ledger

        System.out.printf("Model update stored for key: %s%n", key); // Log para debugging
    }

    /**
     * Realiza la agregación de modelos para una ronda dada.
     * ctx: Contexto.
     * roundNumber: Ronda a agregar.
     * expectedUpdates: Número de nodos que se espera que envíen actualización (para nuestro prototipo será 2).
     * return: El modelo agregado en formato JSON.
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT) // SUBMIT porque modifica el ledger
    public String performAggregation(final Context ctx, final String roundNumber, final int expectedUpdates) {
        ChaincodeStub stub = ctx.getStub();
        System.out.printf("Attempting aggregation for round: %s, expecting %d updates%n", roundNumber, expectedUpdates);

        List<ModelUpdate> updates = new ArrayList<>();
        List<String> contributingNodeIds = new ArrayList<>();

        // --- IMPORTANTE: Simplificación para el prototipo ---
        // Asumimos que sabemos los IDs de los nodos ("Node1", "Node2").
        // En un caso real, necesitarías una forma de descubrir qué nodos participaron
        // o consultar por un prefijo de clave (ej. "UPDATE_1_*").
        List<String> nodeIdsToTry = List.of("Node1", "Node2"); // Hardcoded para el prototipo

        for (String nodeId : nodeIdsToTry) {
            String key = String.format("UPDATE_%s_%s", roundNumber, nodeId);
            String updateJson = stub.getStringState(key); // Lee del ledger

            if (updateJson != null && !updateJson.isEmpty()) {
                try {
                    ModelUpdate update = genson.deserialize(updateJson, ModelUpdate.class);
                    updates.add(update);
                    contributingNodeIds.add(nodeId);
                    System.out.printf("Found update from %s for round %s%n", nodeId, roundNumber);
                } catch (Exception e) {
                    System.err.printf("Error deserializing update for key %s: %s%n", key, e.getMessage());
                    // Decidir si continuar o fallar
                }
            } else {
                 System.out.printf("No update found for key %s%n", key);
            }
        }

        // Verifica si tenemos suficientes actualizaciones
        if (updates.size() < expectedUpdates) {
            String message = String.format("Aggregation failed for round %s: Received %d updates, expected %d",
                                           roundNumber, updates.size(), expectedUpdates);
            System.err.println(message);
            throw new ChaincodeException(message);
        }
        System.out.printf("Proceeding with aggregation for round %s with %d updates.%n", roundNumber, updates.size());

        // --- Lógica de Agregación: Federated Averaging (Promedio Simple) ---
        if (updates.isEmpty()) {
             throw new ChaincodeException("Cannot aggregate with zero updates.");
        }
        // Asume que todos los vectores de pesos tienen el mismo tamaño
        int numWeights = updates.get(0).getWeights().size();
        List<Double> aggregatedWeights = new ArrayList<>(Collections.nCopies(numWeights, 0.0));

        for (ModelUpdate update : updates) {
            List<Double> weights = update.getWeights();
            if (weights.size() != numWeights) {
                throw new ChaincodeException("Inconsistent weight vector sizes among updates.");
            }
            for (int i = 0; i < numWeights; i++) {
                aggregatedWeights.set(i, aggregatedWeights.get(i) + weights.get(i));
            }
        }

        // Calcular el promedio
        for (int i = 0; i < numWeights; i++) {
            aggregatedWeights.set(i, aggregatedWeights.get(i) / updates.size());
        }
        // --- Fin Lógica de Agregación ---
        System.out.printf("Aggregation complete for round %s. Aggregated weights: %s%n", roundNumber, aggregatedWeights.toString());

        // Crear y guardar el modelo agregado
        AggregatedModel aggregatedModel = new AggregatedModel(roundNumber, aggregatedWeights, contributingNodeIds);
        String aggregatedModelJson = genson.serialize(aggregatedModel);
        String aggKey = String.format("AGGREGATED_MODEL_%s", roundNumber);
        stub.putStringState(aggKey, aggregatedModelJson); // Guarda en el ledger
        System.out.printf("Aggregated model stored for key: %s%n", aggKey);

        // (Opcional) Podrías borrar las claves UPDATE_* de esta ronda aquí

        return aggregatedModelJson; // Devuelve el resultado
    }

    /**
     * Obtiene el modelo agregado para una ronda específica.
     * ctx: Contexto.
     * roundNumber: Ronda del modelo a consultar.
     * return: El modelo agregado en formato JSON, o error si no existe.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE) // EVALUATE porque solo lee, no modifica
    public String getAggregatedModel(final Context ctx, final String roundNumber) {
        ChaincodeStub stub = ctx.getStub();
        String key = String.format("AGGREGATED_MODEL_%s", roundNumber);
        System.out.printf("Querying aggregated model for key: %s%n", key);

        String aggregatedModelJson = stub.getStringState(key); // Lee del ledger

        if (aggregatedModelJson == null || aggregatedModelJson.isEmpty()) {
            String message = String.format("Aggregated model for round %s not found.", roundNumber);
            System.err.println(message);
            throw new ChaincodeException(message);
        }

         System.out.printf("Found aggregated model for round %s.%n", roundNumber);
        return aggregatedModelJson;
    }

     // --- Puedes añadir más funciones si lo necesitas, como getModelUpdateHistory ---

} // Fin de la clase FLContract
