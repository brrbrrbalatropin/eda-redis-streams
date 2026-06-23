package edu.eci.arsw;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.StreamEntryID;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class Productor {

    public static void main(String[] args) throws InterruptedException {
        Jedis jedis = new Jedis("localhost", 6379);
        System.out.println("Productor conectado a Redis");

        for (int i = 1; i <= 5; i++) {
            Map<String, String> evento = new HashMap<>();
            evento.put("eventType", "TransferenciaCreada");
            evento.put("eventId", "evt-" + (1000 + i));
            evento.put("transferId", "tr-" + (900 + i));
            evento.put("from", "cta-" + (100 * i));
            evento.put("to", "cta-" + (100 * i + 1));
            evento.put("amount", String.valueOf(50000 * i));
            evento.put("currency", "COP");
            evento.put("createdAt", LocalDateTime.now().toString());

            StreamEntryID id = jedis.xadd("banco.transferencias",
                    StreamEntryID.NEW_ENTRY,
                    evento);

            System.out.println("Evento " + i + " publicado con ID: " + id);
            Thread.sleep(1000);
        }

        jedis.close();
        System.out.println("Productor terminó.");
    }
}