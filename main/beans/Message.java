package beans;

import java.util.HashMap;
import java.util.Map;

/** Messaggio scambiato via socket tra i nodi (serializzato con Gson). */
public class Message {

    public enum Type {
        /** Sei il nuovo successivo del mittente-destinatario indicato in house (incondizionato). */
        SET_NEXT,
        /** Il tuo predecessore è house (incondizionato, usato in uscita). */
        SET_PREVIOUS,
        /** Richiesta di ingresso nell'anello: house è chi entra. */
        JOIN,
        /** Ingresso accettato: house è il vecchio predecessore di chi risponde. */
        JOIN_ACK,
        /** Ingresso rifiutato: house è il nodo da contattare al posto mio. */
        JOIN_REDIRECT,
        /** Consumo locale in viaggio verso l'admin. */
        CONSUME,
        /** Secondo giro del consumo, serve a contare le case. */
        CONSUME_ROUND,
        /** Consumo globale da stampare. */
        GLOBAL_CONSUME,
        /** Uscita dell'admin: chi riceve diventa admin. */
        NEW_ADMIN,
        /** Token verso il successivo. */
        TOKEN_FORWARD,
        /** Token verso il predecessore. */
        TOKEN_BACKWARD
    }

    private Type type;
    private House house;
    private Consume consume;
    private HashMap<Integer, Consume> consumeList;
    private int housesNumber = 1;

    private Message(Type type) {
        this.type = type;
    }

    public static Message of(Type type) {
        return new Message(type);
    }

    public static Message withHouse(Type type, House house) {
        Message m = new Message(type);
        m.house = house;
        return m;
    }

    public static Message withConsume(Type type, Consume consume) {
        Message m = new Message(type);
        m.consume = consume;
        return m;
    }

    public static Message newAdmin(House previous, Map<Integer, Consume> partialConsumes) {
        Message m = new Message(Type.NEW_ADMIN);
        m.house = previous;
        m.consumeList = new HashMap<>(partialConsumes);
        return m;
    }

    public Type getType() { return type; }
    public House getHouse() { return house; }
    public Consume getConsume() { return consume; }
    public HashMap<Integer, Consume> getConsumeList() { return consumeList; }
    public int getHousesNumber() { return housesNumber; }
    public void addNumber() { housesNumber++; }
}