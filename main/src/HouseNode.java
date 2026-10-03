package src;

import beans.Consume;
import beans.House;
import beans.Message;
import beans.TimeUtil;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import simulation.Buffer;
import simulation.Measurement;
import simulation.SmartMeterSimulator;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.ws.rs.core.MediaType;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static beans.Message.Type.*;

public class HouseNode {
    private static final Logger LOGGER = Logger.getLogger("global");

    private static final String REST_BASE = "http://localhost:1025/houses/";
    private static final int PORT_OFFSET = 1025;
    private static final int MAX_ID = 65535 - PORT_OFFSET;
    private static final int WINDOW_SIZE = 24;
    private static final long TOKEN_DELAY_MS = 10;
    private static final long LEAVE_GRACE_MS = 5000;
    private static final long DRAIN_TIMEOUT_S = 30;

    private static final long LINGER_MS = 5000;
    private volatile boolean leaving = false;

    private final Gson gson = new Gson();
    //Client con POJO mapping per add/delete casa; client di default per i consumi (come nell'originale,
    //per non cambiare la serializzazione JSON)
    private final Client houseClient;
    private final Client consumeClient = Client.create();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    private final Object ringLock = new Object();
    private House next;                                              // ringLock
    private House previous;                                          // ringLock
    private boolean admin = false;                                   // ringLock
    private final Map<Integer, Consume> partialConsumes = new HashMap<>(); // ringLock

    private volatile House self;
    private volatile SmartMeterSimulator simulator;
    private volatile ServerSocket serverSocket;
    private final AtomicBoolean needToken = new AtomicBoolean(false);
    private volatile JButton boostButton;

    public static void main(String[] args) {
        new HouseNode().run();
    }

    private HouseNode() {
        ClientConfig config = new DefaultClientConfig();
        config.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, true);
        houseClient = Client.create(config);
    }

    // ------------------------------------------------------------------ avvio e ingresso

    private void run() {
        Scanner scan = new Scanner(System.in);
        System.out.println("Benvenuto!");

        List<House> ring = null;
        while (ring == null) {
            int id = askId(scan);
            House candidate = new House(id, "localhost", id + PORT_OFFSET);

            ClientResponse response = houseClient.resource(REST_BASE + "add_casa")
                    .type(MediaType.APPLICATION_JSON).post(ClientResponse.class, candidate);
            if (response.getStatus() != 200) {
                System.out.println("L'id inserito è già occupato (o non valido), inserirne un altro");
                response.close();
                continue;
            }
            ring = response.getEntity(new GenericType<List<House>>() {});
            for (House h : ring) {
                if (h.getId() == id)
                    self = h; // contiene il timestamp assegnato dal server
            }
        }

        joinRing(ring);
        SwingUtilities.invokeLater(this::createGui);
    }

    private int askId(Scanner scan) {
        while (true) {
            System.out.println("Inserire l'id desiderato (intero tra 1 e " + MAX_ID + ")");
            try {
                int id = Integer.parseInt(scan.nextLine().trim());
                if (id >= 1 && id <= MAX_ID)
                    return id;
            } catch (NumberFormatException ignored) {
                // cade nel messaggio sotto
            }
            System.out.println("Id non valido");
        }
    }

    private void joinRing(List<House> ring) {
        try {
            startServer();
        } catch (IOException e) {
            abortJoin("Impossibile aprire la porta: " + e.getMessage());
        }

        if (ring.size() == 1) {
            //Primo nodo: è l'admin, l'anello è lui stesso, parte un token per direzione
            synchronized (ringLock) {
                next = self;
                previous = self;
                admin = true;
            }
            startSimulator();
            sendMessage(self, Message.of(TOKEN_FORWARD));
            sendMessage(self, Message.of(TOKEN_BACKWARD));
            return;
        }

        //Si parte dal nodo più vecchio; se è in corso un altro ingresso si può essere rimandati altrove
        House target = ring.get(0);
        for (int attempt = 0; attempt <= ring.size(); attempt++) {
            Message reply = request(target, Message.withHouse(JOIN, self));
            if (reply == null)
                abortJoin("Nessuna risposta da " + target.getId());

            if (reply.getType() == JOIN_ACK) {
                synchronized (ringLock) {
                    next = target;
                    previous = reply.getHouse();
                }
                startSimulator();
                return;
            }
            target = reply.getHouse(); // JOIN_REDIRECT
        }
        abortJoin("Impossibile inserirsi nell'anello");
    }

    private void abortJoin(String reason) {
        LOGGER.severe(reason);
        try {
            houseClient.resource(REST_BASE + "delete_casa?id=" + self.getId()).delete(ClientResponse.class).close();
        } catch (RuntimeException e) {
            LOGGER.warning("Impossibile rimuovere la casa dal server: " + e.getMessage());
        }
        System.exit(1);
    }

    private void startSimulator() {
        simulator = new SmartMeterSimulator(new ConsumeBuffer());
        simulator.start();
    }

    // ------------------------------------------------------------------ GUI

    private void createGui() {
        JFrame frame = new JFrame("Home " + self.getId());
        frame.setSize(310, 100);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new FlowLayout());

        boostButton = new JButton("BOOST");
        boostButton.setPreferredSize(new Dimension(75, 35));
        JButton exit = new JButton("EXIT");
        exit.setPreferredSize(new Dimension(75, 35));
        frame.add(boostButton);
        frame.add(exit);

        boostButton.addActionListener(e -> {
            needToken.set(true);
            boostButton.setEnabled(false);
        });
        exit.addActionListener(e -> {
            exit.setEnabled(false);
            //Fuori dall'EDT: la chiamata REST e le attese non devono bloccare la GUI
            new Thread(() -> leaveRing(frame, exit), "leave-ring").start();
        });

        frame.setVisible(true);
    }

    // ------------------------------------------------------------------ uscita

    private void leaveRing(JFrame frame, JButton exit) {
        ClientResponse response = houseClient
                .resource(REST_BASE + "delete_casa?id=" + self.getId()).delete(ClientResponse.class);
        int status = response.getStatus();
        response.close();
        if (status != 200) {
            System.out.println("C'è stato un problema con la cancellazione");
            SwingUtilities.invokeLater(() -> exit.setEnabled(true));
            return;
        }

        simulator.stopMeGently();
        try {
            Thread.sleep(LEAVE_GRACE_MS);
            leaving = true;                 // da ora inoltro soltanto
            notifyNeighbours();
            Thread.sleep(LINGER_MS);        // continuo a inoltrare i messaggi in ritardo
            pool.shutdown();
            if (!pool.awaitTermination(DRAIN_TIMEOUT_S, TimeUnit.SECONDS))
                LOGGER.warning("Alcuni messaggi in sospeso non sono terminati in tempo");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        closeQuietly(serverSocket);
        SwingUtilities.invokeLater(frame::dispose);
        System.out.println("Eliminazione dalla rete completata");
    }

    private void notifyNeighbours() {
        House n, p;
        boolean wasAdmin;
        Map<Integer, Consume> partial = null;
        synchronized (ringLock) {
            n = next;
            p = previous;
            wasAdmin = admin;
            if (wasAdmin)
                partial = new HashMap<>(partialConsumes);
        }
        if (n == null || p == null || n.getId() == self.getId())
            return; // unico nodo dell'anello: nessuno da avvisare

        //Il mio predecessore ora ha come successivo il mio successivo...
        sendMessage(p, Message.withHouse(SET_NEXT, n));
        //...e il mio successivo ha come predecessore il mio predecessore (e, se ero admin, diventa admin)
        if (wasAdmin)
            sendMessage(n, Message.newAdmin(p, partial));
        else
            sendMessage(n, Message.withHouse(SET_PREVIOUS, p));
    }

    // ------------------------------------------------------------------ server socket

    private void startServer() throws IOException {
        serverSocket = new ServerSocket(self.getPort());
        LOGGER.info("Socket instanziato, accetto connessioni");

        Thread acceptor = new Thread(() -> {
            try {
                while (true) {
                    Socket socket = serverSocket.accept();
                    try {
                        pool.execute(() -> handleConnection(socket));
                    } catch (RejectedExecutionException e) {
                        closeQuietly(socket); // sto uscendo
                    }
                }
            } catch (SocketException e) {
                LOGGER.info("Socket chiusa");
            } catch (IOException e) {
                LOGGER.severe("Problemi con la connessione: " + e.getMessage());
            } finally {
                closeQuietly(serverSocket);
            }
        }, "acceptor");
        acceptor.start();
    }

    private void handleConnection(Socket socket) {
        try (Socket s = socket) {
            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            Message m = gson.fromJson(in.readUTF(), Message.class);
            dispatch(m, out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            LOGGER.severe("Problemi con la connessione: " + e.getMessage());
        }
    }

    private void dispatch(Message m, DataOutputStream out) throws IOException, InterruptedException {
        switch (m.getType()) {
            case SET_NEXT:
                synchronized (ringLock) { next = m.getHouse(); }
                break;
            case SET_PREVIOUS:
                synchronized (ringLock) { previous = m.getHouse(); }
                break;
            case JOIN:
                handleJoin(m.getHouse(), out);
                break;
            case CONSUME:
                handleConsume(m);
                break;
            case CONSUME_ROUND:
                handleConsumeRound(m);
                break;
            case GLOBAL_CONSUME:
                handleGlobalConsume(m);
                break;
            case NEW_ADMIN:
                handleNewAdmin(m);
                break;
            case TOKEN_FORWARD:
                handleToken(m, true);
                break;
            case TOKEN_BACKWARD:
                handleToken(m, false);
                break;
            default:
                LOGGER.warning("Messaggio inatteso: " + m.getType());
        }
    }

    // ------------------------------------------------------------------ gestori dei messaggi

    //Un nodo chiede di diventare mio predecessore. Il timestamp decide chi va prima in caso di ingressi concorrenti.
    private void handleJoin(House joiner, DataOutputStream out) throws IOException {
        House oldPrevious;
        boolean redirect;
        synchronized (ringLock) {
            oldPrevious = previous;
            redirect = oldPrevious != null && joiner.getTimestamp() < oldPrevious.getTimestamp();
            if (!redirect)
                previous = joiner;
        }

        if (redirect) {
            out.writeUTF(gson.toJson(Message.withHouse(JOIN_REDIRECT, oldPrevious), Message.class));
            return;
        }
        out.writeUTF(gson.toJson(Message.withHouse(JOIN_ACK, oldPrevious), Message.class));
        //Avviso il vecchio predecessore che il suo successivo ora è chi è entrato
        sendMessage(oldPrevious, Message.withHouse(SET_NEXT, joiner));
    }

    //Consumo locale: inoltrato fino all'admin, che lo registra e avvia il secondo giro
    private void handleConsume(Message m) {
        if (!isAdmin()) {
            sendMessage(getNext(), m);
            return;
        }
        if (postConsume(m.getConsume()))
            sendMessage(getNext(), Message.withConsume(CONSUME_ROUND, m.getConsume()));
    }

    //Secondo giro: ogni nodo si conta, l'admin raccoglie e quando ha tutte le case calcola il globale.
    //Se una casa entra/esce durante il giro, le voci vecchie vengono scartate al clear() successivo.
    private void handleConsumeRound(Message m) {
        if (!isAdmin()) {
            if (!leaving)
                m.addNumber();
            sendMessage(getNext(), m);
            return;
        }

        Consume global = null;
        synchronized (ringLock) {
            partialConsumes.put(m.getConsume().getHouseId(), m.getConsume());
            if (m.getHousesNumber() <= partialConsumes.size()) {
                double total = 0;
                for (Consume c : partialConsumes.values())
                    total += c.getConsume();
                partialConsumes.clear();
                global = new Consume(0, total, TimeUtil.millisSinceMidnight());
            }
        }
        //La chiamata REST avviene fuori dal lock
        if (global != null && postConsume(global))
            sendMessage(getNext(), Message.withConsume(GLOBAL_CONSUME, global));
    }

    private void handleGlobalConsume(Message m) {
        Consume c = m.getConsume();
        System.out.println("Consumo globale: (" + TimeUtil.format(c.getTimestamp()) + ") " + c.getConsume() + " kW");
        if (!isAdmin())
            sendMessage(getNext(), m); // l'admin chiude il giro
    }

    private void handleNewAdmin(Message m) {
        synchronized (ringLock) {
            admin = true;
            partialConsumes.clear();
            if (m.getConsumeList() != null)
                partialConsumes.putAll(m.getConsumeList());
            previous = m.getHouse();
        }
    }

    private void handleToken(Message m, boolean forward) throws InterruptedException {
        Thread.sleep(TOKEN_DELAY_MS);
        if (!leaving && needToken.compareAndSet(true, false)) {
            simulator.boost();
            SwingUtilities.invokeLater(() -> boostButton.setEnabled(true));
        }
        sendMessage(forward ? getNext() : getPrevious(), m);
    }

    // ------------------------------------------------------------------ utilità

    private House getNext() { synchronized (ringLock) { return next; } }
    private House getPrevious() { synchronized (ringLock) { return previous; } }
    private boolean isAdmin() { synchronized (ringLock) { return admin && !leaving; } }

    private boolean postConsume(Consume c) {
        ClientResponse response = consumeClient.resource(REST_BASE + "add_consumo")
                .type(MediaType.APPLICATION_JSON).post(ClientResponse.class, c);
        try {
            if (response.getStatus() == 200)
                return true;
            System.out.println("C'è stato un problema con l'inserimento del consumo");
            return false;
        } finally {
            response.close();
        }
    }

    private void sendMessage(House who, Message message) {
        if (who == null || message == null)
            return;
        try (Socket socket = new Socket(who.getIpAddress(), who.getPort())) {
            new DataOutputStream(socket.getOutputStream()).writeUTF(gson.toJson(message, Message.class));
        } catch (IOException e) {
            LOGGER.severe("Problemi con la connessione verso la casa " + who.getId() + ": " + e.getMessage());
        }
    }

    //Invia e attende la risposta (usato per l'ingresso)
    private Message request(House who, Message message) {
        try (Socket socket = new Socket(who.getIpAddress(), who.getPort())) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            out.writeUTF(gson.toJson(message, Message.class));
            return gson.fromJson(in.readUTF(), Message.class);
        } catch (IOException e) {
            LOGGER.severe("Problemi con la connessione verso la casa " + who.getId() + ": " + e.getMessage());
            return null;
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c == null)
            return;
        try {
            c.close();
        } catch (IOException e) {
            LOGGER.warning("Problemi con la chiusura: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ buffer del simulatore

    private class ConsumeBuffer implements Buffer {
        private final List<Measurement> window = new ArrayList<>();

        @Override
        public synchronized void addMeasurement(Measurement m) {
            window.add(m);
            if (window.size() == WINDOW_SIZE) {
                publishAverage();
                window.subList(0, WINDOW_SIZE / 2).clear();
            }
        }

        private void publishAverage() {
            double average = window.stream().mapToDouble(Measurement::getValue).average().orElse(0);
            Consume c = new Consume(self.getId(), average, TimeUtil.millisSinceMidnight());
            //Nessun lock di rete e nessuno sleep: la misura va semplicemente inviata al successivo
            sendMessage(getNext(), Message.withConsume(CONSUME, c));
        }
    }
}
 
