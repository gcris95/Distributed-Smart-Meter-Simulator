package src;

import beans.House;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.util.List;
import java.util.Scanner;

public class ClientAdmin {
    private static final String BASE = "http://localhost:1025/admin/";
    private static final String MENU =
            "1) Visualizza la lista delle case\n" +
                    "2) Visualizza consumi condominio\n" +
                    "3) Visualizza media condominio\n" +
                    "4) Visualizza deviazione standard condominio\n" +
                    "5) Visualizza consumi casa\n" +
                    "6) Visualizza media casa\n" +
                    "7) Visualizza deviazione standard casa\n" +
                    "0) Esci";

    public static void main(String[] args) {
        ClientConfig clientConfig = new DefaultClientConfig();
        clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, true);

        Client client = Client.create(clientConfig);
        Scanner scan = new Scanner(System.in);

        System.out.println("Benvenuto, scegli una delle seguenti opzioni inserendo il numero corrispondente:\n" +
                MENU);

        int input;

        while ((input = readInt(scan)) != 0) {
            switch (input) {
                case 1:
                    showHouses(client);
                    break;
                case 2:
                case 3:
                case 4:
                    showStat(client, scan, input, false);
                    break;
                case 5:
                case 6:
                case 7:
                    showStat(client, scan, input, true);
                    break;
                default:
                    System.out.println("Scelta non valida");
            }
            System.out.println(MENU);
        }
        System.out.println("Arrivederci");
        scan.close();
    }

    private static int readInt(Scanner scan) {
        while (true) {
            try {
                return Integer.parseInt(scan.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Non è permesso inserire caratteri, riprova");
            }
        }
    }

    private static void showHouses(Client client) {
        ClientResponse response = client.resource(BASE + "get_house").accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);

        if (response.getStatus() == 204) {
            System.out.println("Attualmente non sono presenti case nel condominio");
        } else if (response.getStatus() == 200) {
            List<House> lista = response.getEntity(new GenericType<List<House>>() {
            });
            for (House house : lista) {
                System.out.println("ID: " + house.getId() + "\n" +
                        "Indirizzo IP: " + house.getIpAddress() + "\n" +
                        "Port: " + house.getPort() +
                        "\n---------------------------------------------------------------");
            }
        } else {
            System.out.println("Errore del server: " + response.getStatus());
        }
        response.close();
    }

    //Opzioni 2-4 (condominio) e 5-7 (singola casa): consumi / media / deviazione standard
    private static void showStat(Client client, Scanner scan, int choice, boolean perHouse) {
        String[] paths = {"get_consumi", "get_media", "get_std"};
        String[] labels = {"gli ultimi n consumi", "la media degli ultimi n consumi", "la deviazione standard degli ultimi n consumi"};
        String of = perHouse ? " di una casa" : " del condominio";
        int idx = (choice - 2) % 3; // 2,5 -> 0 | 3,6 -> 1 | 4,7 -> 2

        System.out.println("Hai scelto di visualizzare " + labels[idx] + of +
                ", scrivi il valore di 'n' per procedere, oppure -1 per tornare indietro");

        int n;
        while (true) {
            n = readInt(scan);
            if (n == -1)
                return;
            if (n > 0)
                break;

            System.out.println("Valore di n non valido, inserire un numero maggiore di 0");
        }

        int id = 0;
        if (perHouse) {
            while (true) {
                System.out.println("Inserisci ora l'id della casa");
                id = readInt(scan);
                if (id > 0)
                    break;

                System.out.print("ID non valido, inserire un valore maggiore di 0");
            }
        }

        ClientResponse response = client.resource(BASE + paths[idx] + "?n=" + n + "&id=" + id).accept(MediaType.TEXT_PLAIN).get(ClientResponse.class);

        if (response.getStatus() == 204) {
            System.out.print("Attualmente non sono disponibili dati sui consumi " + (perHouse ? "della casa scelta" : "del condominio"));
        } else if (response.getStatus() == 200) {
            System.out.println(response.getEntity(String.class));
        } else {
            System.out.println("Errore dal server: " + response.getStatus());
        }
        response.close();
    }
}
