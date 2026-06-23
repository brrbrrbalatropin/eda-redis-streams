package edu.eci.arsw;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;

import java.util.List;
import java.util.Map;

public class Consumidor {

    private static volatile boolean corriendo = true;

    public static void main(String[] args) throws InterruptedException {
        String grupo = args.length > 0 ? args[0] : "fraude-group";
        String consumidor = args.length > 1 ? args[1] : "consumidor-1";

        Jedis jedis = new Jedis("localhost", 6379);
        System.out.println("Consumidor [" + grupo + " / " + consumidor + "] conectado");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[" + grupo + "] Apagando consumidor...");
            corriendo = false;
            jedis.close();
        }));

        while (corriendo) {
            List<Map.Entry<String, List<StreamEntry>>> resultado = jedis.xreadGroup(
                    grupo,
                    consumidor,
                    XReadGroupParams.xReadGroupParams().count(1).block(3000),
                    Map.of("banco.transferencias", StreamEntryID.UNRECEIVED_ENTRY)
            );

            if (resultado != null && !resultado.isEmpty()) {
                for (Map.Entry<String, List<StreamEntry>> entry : resultado) {
                    for (StreamEntry streamEntry : entry.getValue()) {
                        System.out.println("[" + grupo + "] Procesando evento: " + streamEntry.getFields());
                        jedis.xack("banco.transferencias", grupo, streamEntry.getID());
                        System.out.println("[" + grupo + "] ACK enviado para: " + streamEntry.getID());
                    }
                }
            } else {
                System.out.println("[" + grupo + "] No hay más eventos. Cerrando consumidor.");
                corriendo = false;
            }
        }

        jedis.close();
        System.out.println("[" + grupo + "] Consumidor cerrado.");
    }
}