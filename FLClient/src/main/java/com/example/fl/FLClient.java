package com.example.fl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.hyperledger.fabric.gateway.*;

public class FLClient {

    private static final String MSP_ID  = "Org1MSP";
    private static final String CHANNEL = "mychannel";
    private static final String CC_NAME = "flcontract";
    private static final String CONTRACT = "FLContract";
    private static final Path   WALLET_PATH = Paths.get("wallet");

    public static void main(String[] args) throws Exception {

        Wallet wallet = prepareWallet();

        System.setProperty("org.hyperledger.fabric.sdk.service_discovery.as_localhost", "true");

        Gateway.Builder builder = Gateway.createBuilder()
                .identity(wallet, "admin")
                .networkConfig(Paths.get("connection.yaml"));
                // ← connectTimeout(...) ya no existe en 2.2.x

        try (Gateway gw = builder.connect()) {

            Network  network = gw.getNetwork(CHANNEL);
            Contract fl      = network.getContract(CC_NAME, CONTRACT);

            int totalRounds = 3;
            List<String> nodes = List.of("Node1", "Node2");

            for (int r = 1; r <= totalRounds; r++) {
                System.out.printf("%n=== ROUND %d ===%n", r);

                for (String n : nodes) {
                    List<Double> w = dummyWeights(n, r);
                    submitUpdate(fl, n, r, w);
                }

                fl.submitTransaction("performAggregation",
                        String.valueOf(r), String.valueOf(nodes.size()));
                System.out.println("  • Aggregation committed");

                byte[] json = fl.evaluateTransaction("getAggregatedModel",
                        String.valueOf(r));
                System.out.println("Aggregated model: " + new String(json));
            }
        }
    }

    /* ---------------- helper methods ---------------- */

    private static Wallet prepareWallet() throws Exception {
        Wallet wallet = Wallets.newFileSystemWallet(WALLET_PATH);
        if (!wallet.list().contains("admin")) {
            Path credPath = Paths.get("msp");
            Path certFile = credPath.resolve("signcerts/cert.pem");
            Path keyFile  = Files.list(credPath.resolve("keystore"))
                                .findFirst().orElseThrow();

            /*  leer el texto PEM  */
            String pemCert = Files.readString(certFile, StandardCharsets.UTF_8);
            String pemKey  = Files.readString(keyFile,  StandardCharsets.UTF_8);

            /*  convertir a objetos  */
            X509Certificate cert = Identities.readX509Certificate(pemCert);
            PrivateKey      key  = Identities.readPrivateKey(pemKey);

            wallet.put("admin", Identities.newX509Identity(MSP_ID, cert, key));
            System.out.println("➜  Wallet populated with admin identity");
        }
        return wallet;
    }

    private static void submitUpdate(Contract fl, String nodeId,
                                     int round, List<Double> weights) throws Exception {

        Map<String,Object> payload = Map.of(
            "nodeId", nodeId,
            "roundNumber", String.valueOf(round),
            "weights", weights);

        String json = new ObjectMapper().writeValueAsString(payload);

        fl.submitTransaction("submitModelUpdate",
                nodeId, String.valueOf(round), json);

        System.out.printf("  • Update from %-5s committed%n", nodeId);
    }

    private static List<Double> dummyWeights(String node, int round) {
        double base = node.equals("Node1") ? 0.1 : 0.2;
        return List.of(base*round, (base+0.1)*round, (base+0.2)*round);
    }
}
