package beans;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Houses {
    //Lista delle case nella rete
    private final ConcurrentHashMap<Integer, House> housesList;
    //Insieme dei consumi complessivi e locali, l'id 0 è dedicato al condominio
    private final ConcurrentHashMap<Integer, List<Consume>> consumeList;

    private long lastTimestamp = 0;
    private static final int MAX_PORT = 65535;

    //costruttore private del singleton
    private Houses() {
        housesList = new ConcurrentHashMap<>();
        consumeList = new ConcurrentHashMap<>();
    }

    private static class InstanceHolder {
        private static final Houses INSTANCE = new Houses();
    }

    public static Houses getInstance() {
        return InstanceHolder.INSTANCE;
    }

    //Verifica se la casa è presente
    public boolean checkHouse(int id){
        return housesList.containsKey(id);
    }

    //Aggiunge una casa all'HashMap dopo aver verificato che questa non sia già presente
    public synchronized List<House> addHouse(House h){
        if(h==null || h.getId()<=0 || h.getPort() <= 0 || h.getPort() > MAX_PORT || housesList.containsKey(h.getId()))
            return null;

        long ts = Math.max(System.currentTimeMillis(), lastTimestamp + 1);
        h.setTimestamp(ts);
        lastTimestamp = ts;

        housesList.put(h.getId(), h);
        return getHouses();
    }

    //Cancella una casa, ritorna false se la casa non è presente nella lista delle case
    public synchronized boolean deleteHouse(int id){
        if (housesList.remove(id) != null) {
            consumeList.remove(id);
            return true;
        }
        return false;
    }

    //Aggiunge una media se la casa è già presente, altrimenti crea una nuova lista
    public void addConsume(Consume c) {
        if(c== null)
            return;

        consumeList.computeIfAbsent(c.getHouseId(), k -> new CopyOnWriteArrayList<>()).add(c);
    }

    //Restituisce la lista delle case nella rete
    public List<House> getHouses(){
        List<House> l = new ArrayList<>(housesList.values());
        l.sort(Comparator.comparingLong(House::getTimestamp));
        return l;
    }

    private List<Consume> lastConsumes(int n, int houseId) {
        List<Consume> list = consumeList.get(houseId);
        if (list == null || n <= 0)
            return Collections.emptyList();
        List<Consume> copy = new ArrayList<>(list);
        return copy.subList(Math.max(0, copy.size() - n), copy.size());
    }

    //Restituisce gli ultimi n consumi di una certa casa con timestamp
    public String getConsumes(int n, int houseId){
        List<Consume> list = lastConsumes(n, houseId);

        if (list.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        int size = list.size();

        for (int i = size - 1; i >= 0; i--) {
            Consume c = list.get(i);
            sb.append("(").append(TimeUtil.format(c.getTimestamp()))
                    .append(") kW: ").append(c.getConsume()).append("\n");
        }

        return sb.toString();
    }

    //Calcola la media degli ultimi n consumi complessivi
    public OptionalDouble calculateAverage(int n, int houseId) {
        return lastConsumes(n, houseId).stream().mapToDouble(Consume::getConsume).average();
    }

    //Calcola la varianza degli ultimi n consumi complessivi
    private OptionalDouble calculateVariance(int n, int houseId){
        List<Consume> last = lastConsumes(n, houseId);
        if (last.isEmpty())
            return OptionalDouble.empty();

        //Media e varianza sullo stesso snapshot, così i due valori sono coerenti
        double avg = last.stream().mapToDouble(Consume::getConsume).average().getAsDouble();
        return last.stream()
                .mapToDouble(c -> (c.getConsume() - avg) * (c.getConsume() - avg))
                .average();
    }

    //Calcola la deviazione standard degli ultimi n consumi complessivi
    public OptionalDouble calculateSTD(int n, int houseId){
        OptionalDouble variance = calculateVariance(n, houseId);
        return variance.isPresent() ? OptionalDouble.of(Math.sqrt(variance.getAsDouble())) : OptionalDouble.empty();
    }
}